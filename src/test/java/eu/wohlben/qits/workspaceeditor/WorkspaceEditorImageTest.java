package eu.wohlben.qits.workspaceeditor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * The pin resolves, and it resolves to a CalVer.
 *
 * <p>The guard on the one thing about {@link WorkspaceEditorImage} that can silently break: the
 * resource-filtering block in this repository's pom. Without filtering the class reads the literal
 * {@code ${project.version}}, and qits-workspaces — which composes an image reference out of it —
 * would start editor containers from something no registry can resolve. That failure surfaces a
 * repository away, as a container that never starts, which is why the check lives here.
 *
 * <p>The <em>shape</em> and not a value: the version is whatever release stamped this tree, so an
 * expected literal would be a line every release has to edit and the first missed edit would fail
 * a correct build.
 */
class WorkspaceEditorImageTest {

  @Test
  void theVersionIsFilteredInAndIsACalVer() {
    String version = WorkspaceEditorImage.VERSION;
    assertTrue(
        version.matches("[0-9][0-9.]*[0-9]"),
        () -> "not a CalVer — is resource filtering still on? got: " + version);
  }

  @Test
  void theRepositoryIsTheNameTheReleasePushesUnder() {
    // A literal, because it is a cross-repository contract: the same string appears in this
    // repository's `artifacts:` declaration and in the ref the buildctl push tags. A rename that
    // moved only one side is what this asserts against.
    assertEquals("qits/workspace-editor", WorkspaceEditorImage.REPOSITORY);
  }
}
