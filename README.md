# qits-workspace-editor-oci

The workspace **editor** image, published as **`qits/workspace-editor`**.

It is the workspace image plus one directory: a pinned
[openvscode-server](https://github.com/gitpod-io/openvscode-server) unpacked at
`/opt/openvscode-server`, and a provenance file at `/etc/qits-editor-provenance` saying which version
came from where. Nothing else. No user, no workdir, no exposed port, no entrypoint — the base's
daemon still is the container's PID 1, and the editor is a process that daemon starts.

    FROM registry.dev.localhost:8080/qits/workspace:<pin>   <- the workspace container image
      + /opt/openvscode-server/…                            <- this repository's whole contribution
      + /etc/qits-editor-provenance

## Why it is a separate image

openvscode-server unpacks to a few hundred megabytes that only a workspace *with the editor turned
on* ever reads. Layering it into `qits/workspace` would make every ordinary workspace pull and carry
them for nothing, so the binary lives in a child image instead — and "does this container have an
editor" becomes a question about which image it was started from, answered before the container
exists, rather than a runtime flag.

The published name is `qits/workspace-editor`, not the repository name, the same split
`qits-workspace-oci` publishing `qits/workspace-base` and `qits-workspace-daemon` publishing
`qits/workspace` already record. The repository name is where the recipe lives; the image name is
what the recipe produces and what qits-workspaces writes.

## What is deliberately not here

- **No launch flags and no connection token.** How the editor is started — its port, its
  `--user-data-dir`, its authentication — is the daemon's business and changes without rebuilding
  this image. This repository only puts the binary at a path the daemon can hard-code.
- **No extension gallery.** Open VSX is not reachable from the platform network, so the editor runs
  on the extensions the distribution bundles. There is nothing to configure here for that; if the
  gallery ever needs to be switched off explicitly it belongs to the launch, not to the recipe.
- **No path prefix.** openvscode-server serves from `/` and this platform does no path rewriting
  anywhere, so a workspace's editor is reached on its own origin. Nothing in this image needs to
  know that, but everything downstream does.
- **No `deployments.yml`.** Nothing *deploys* this image: qits-workspaces starts a container from it
  per workspace, exactly as it does from `qits/workspace`. Same case as `qits-workspace-oci`.

## The two pins

Both live in `Dockerfile`, both as `ARG`s with the pin as the default, so CI passes no
`--build-arg` and rebuilding a released tag rebuilds exactly what that tag shipped.

| Pin | Line | Who moves it |
|---|---|---|
| `WORKSPACE_IMAGE` | `ARG WORKSPACE_IMAGE=registry.dev.localhost:8080/qits/workspace:<version>` | qits-maintenance, automatically |
| `OPENVSCODE_VERSION` + `OPENVSCODE_SHA256` | two `ARG`s | a human, in one commit, together |

`OPENVSCODE_SHA256` is part of the pin and not decoration: the URL names an immutable GitHub release
asset, and an asset that is deleted and re-uploaded under the same name would otherwise substitute
itself into the image silently. Bumping the version without the sum fails the build, which is the
intent.

## How the base pin moves

`qits-workspace-daemon` releases `qits/workspace`; this repository follows it — and there is no
pipeline here that does the following. **qits-maintenance does**, the same way it moves a maven
property or an npm dependency:

1. It inventories this repository's Dockerfile and records the literal `ARG WORKSPACE_IMAGE=` default
   as a **docker pin** at `arg:WORKSPACE_IMAGE`. The registry host is dropped when the image is
   matched, so the pin's name is `qits/workspace` — the image qits-workspace-daemon publishes.
2. A newer `qits/workspace` in the registry makes the pin outdated. Maintenance rewrites the tag on
   that one line, on a branch of its own, fast-forward and never forced.
3. That branch builds nothing by itself — it is a **source**. Maintenance opens a **release request**
   naming it, qits-projects folds it onto `release/<id>`, and
   `.config/qits/ci-event-release-request.yml` builds the fold. A gating green verdict is what lets
   Auto Release stamp the CalVer and tag.
4. `.config/qits/ci-event-release.yml` rebuilds at the tag and pushes
   `qits/workspace-editor:<version>`.

Nobody in the loop, and nothing repository-local to keep matched. This used to be a **hop file** —
`.config/qits/ci-event-upstream-workspace-daemon.yml`, a pipeline watching a `SoftwareRelease`,
`sed`ing the line and force-pushing `maintenance/qits-workspace-daemon`. Its whole job is now a row
in the maintenance inventory, so it is gone: one fewer per-repository copy of a mechanism, and one
fewer event selector to get quietly wrong. (The hop's most expensive lesson was exactly that — a
`repository:` selector holding a *name* matched nothing after the 2026-08-22 identity cutover and
left the identical train in `qits-workspace-daemon` dead for a week.)

**The pin's literal shape is the contract now.** Maintenance reads a docker pin only from a literal
`ARG <NAME>=<image>:<tag>`; a value carrying `$`, `@` or `://`, or with no `/` before the tag, is
never a pin. Rewriting that line into an indirection would not fail anything — it would just stop the
base from following the toolchain, silently.

## Lifecycle

1. **QA** — a release request naming a branch of this repository folds onto `release/<id>`, and
   `.config/qits/ci-event-release-request.yml` builds and pushes `qits/workspace-editor:<sha>` at
   that fold. There is no test step and none is missing: this repository holds a Dockerfile and no
   code, so the build *is* the test — which is also why every step here gates.
2. **Release** — on a gating green verdict qits-projects' Auto Release stamps a CalVer, writes the
   annotated tag and announces `SCMRelease`.
   `.config/qits/ci-event-release.yml` builds *at the tag* and pushes
   `qits/workspace-editor:<version>`; its green run makes qits-ci announce one `SoftwareRelease`.
3. **Consumption** — qits-workspaces pins the editor image by version and starts a container from it
   when a workspace asks for an editor. A released image goes live on the next deploy of the pinning
   service; containers already running are untouched.

One tag per pipeline, always: the QA pipeline owns the sha coordinate, the release pipeline owns
the version coordinate, and neither writes the other's. `docker build -t A -t B` followed by two
pushes fails the second with "tag does not exist" — BuildKit's exporter does not reliably leave every
alias of a multi-tag build in the local image store.

## Building by hand

    docker build -t qits/workspace-editor:local .

Against a hand-built workspace rather than the pinned release:

    docker build -t qits/workspace-editor:local . \
      --build-arg WORKSPACE_IMAGE=qits/workspace:native

Expect the base pull to dominate: the workspace image is ~3.4 GB, and this repository adds a ~77 MB
download on top of it. CI allows two hours for both pipelines.

## The provenance file

    $ docker run --rm qits/workspace-editor:local cat /etc/qits-editor-provenance
    openvscode-version=1.109.5
    openvscode-url=https://github.com/gitpod-io/openvscode-server/releases/download/openvscode-server-v1.109.5/openvscode-server-v1.109.5-linux-x64.tar.gz
    openvscode-sha256=b433bf4f0227321a7014d8460d10a8f958adc0f45aa79bd889e84e65e8f88363
    openvscode-home=/opt/openvscode-server
    workspace-image=registry.dev.localhost:8080/qits/workspace:2026.823.71954

It records the base image too, so one read of a live workspace says both which editor and which
workspace are running — without a registry lookup, and without trusting the tag the container was
started under.

## Smoke test, at the pin

The image cannot be exercised by CI beyond "it builds": nothing here starts a server, and the daemon
that would is in another repository. So when this pin moves — either pin — run the editor once by
hand and check the four things that break independently:

    docker run --rm -it -p 3000:3000 --entrypoint /opt/openvscode-server/bin/openvscode-server \
      qits/workspace-editor:local --host 0.0.0.0 --port 3000 --without-connection-token

- the workbench **renders at `/`**, not at a sub-path;
- **static assets** load from the root origin (no 404s in the network panel);
- the **websocket** to the extension host connects and stays up — a dead one shows as a workbench
  that paints and then never opens a file;
- a **webview** (the Markdown preview is the cheapest) renders, since webviews are served from their
  own origin and fail separately from everything above.

`--entrypoint` is required: the image's entrypoint is the workspace daemon, on purpose.
