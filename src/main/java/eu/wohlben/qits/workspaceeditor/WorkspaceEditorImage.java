package eu.wohlben.qits.workspaceeditor;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * <b>What this jar was released with</b>: the {@code qits/workspace-editor} image tag of the release
 * that published this artifact.
 *
 * <p>This whole jar exists to be depended on. qits-workspaces starts an editor workspace from this
 * image, and until now it learned which version from {@code env.QITS_EDITOR_IMAGE_VERSION} — a
 * qits-configuration entry a release listener rewrote the moment an image was pushed here. Two
 * things followed from that, and both were real: a new editor image was used by the next workspace
 * with no test of the pair, and the fallback default shipped in qits-workspaces' properties file
 * aged in silence until it named an image the registry's retention had deleted, so any run without
 * the injection started from a reference that could not be pulled.
 *
 * <p>Depending on a coordinate fixes both. The dependency has to <em>resolve</em> for the consumer
 * to build, so a version that does not exist is a red build rather than a failed pull much later;
 * the maintenance train moves the pom line like any internal library; and the consumer's own
 * release request is what gates the move.
 *
 * <p><b>The value is {@code ${project.version}}, resolved at build time</b> into {@code
 * workspace-editor-image.properties} beside this class rather than written down. The release stamps
 * the pom with the version it is about to tag, and the same release pushes the image under that tag
 * — so "the version of this jar" and "the image tag" are one string by construction. A build from a
 * working tree names the previous release, which is honest: nothing has been published for the tree
 * in hand.
 *
 * <p>No framework, no configuration system, nothing but a {@code Properties} load: the consumer is
 * a Quarkus service and this jar must not have an opinion about that.
 */
public final class WorkspaceEditorImage {

  /** The resource the build filters {@code ${project.version}} into, beside this class. */
  private static final String RESOURCE = "workspace-editor-image.properties";

  /**
   * The image repository an editor workspace is started from, unqualified — the reader supplies its
   * own registry host. Unqualified for the reason {@code .config/qits/ci-event-release.yml} gives
   * for the {@code artifacts:} declaration it mirrors: the registry is one service under several
   * addresses, and an OCI reference cannot carry a path prefix.
   *
   * <p>It is {@code qits/workspace-editor} and not this repository's name, which is the same split
   * {@code qits-workspace-oci} publishing {@code qits/workspace-base} already records: the
   * repository name is where the recipe lives, the image name is what the recipe produces.
   */
  public static final String REPOSITORY = "qits/workspace-editor";

  /** The released version: the {@code qits/workspace-editor} tag this jar was published beside. */
  public static final String VERSION = readVersion();

  private static String readVersion() {
    Properties p = new Properties();
    try (InputStream in = WorkspaceEditorImage.class.getResourceAsStream(RESOURCE)) {
      if (in == null) {
        // Not recoverable and deliberately not defaulted: a consumer that silently started ""
        // or "latest" is the exact failure this class exists to remove.
        throw new IllegalStateException(
            RESOURCE + " is not on the classpath beside " + WorkspaceEditorImage.class.getName());
      }
      p.load(in);
    } catch (IOException e) {
      throw new IllegalStateException("cannot read " + RESOURCE, e);
    }
    String version = p.getProperty("version", "");
    if (version.isBlank() || version.startsWith("$")) {
      // `$` catches the one mistake that would otherwise ship: resource filtering switched off,
      // leaving the literal `${project.version}` to be used as an image tag.
      throw new IllegalStateException(RESOURCE + " carries no resolved version: '" + version + "'");
    }
    return version;
  }

  private WorkspaceEditorImage() {}
}
