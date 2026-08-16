package com.johnnyblabs.openspec.search;

import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import com.johnnyblabs.openspec.model.Requirement;
import com.johnnyblabs.openspec.model.SpecFile;

import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.ListCellRenderer;
import java.awt.Component;

/**
 * Renderer-behavior guard for {@link SpecSearchEverywhereContributor#getElementsRenderer()}.
 *
 * <p>The renderer was migrated off the scheduled-for-removal {@code SimpleListCellRenderer.create(...)}
 * factory onto a direct {@code SimpleListCellRenderer} subclass. This pins that the migrated
 * {@code customize} still sets the row <b>text</b> from {@code presentableText()} (the requirement
 * name) and the <b>tooltip</b> from {@code locationText()} (the owning capability), and that the
 * {@code if (value != null)} guard still prevents an NPE on the null cells Search Everywhere passes
 * during list churn. The tooltip is asserted here because nothing else covers it — the uiSmoke
 * Search-Everywhere journey checks the row text only, and at the release tier.
 *
 * <p>Runs as a {@link BasePlatformTestCase} (not plain JUnit) because {@code SimpleListCellRenderer}
 * extends {@code JBLabel} and needs the platform application loaded; the base runs on the EDT, so
 * driving the renderer is safe.
 */
public class SpecSearchRendererTest extends BasePlatformTestCase {

    public void testRendererShowsRequirementNameAsTextAndCapabilityAsTooltip() {
        SpecRequirementMatch match = new SpecRequirementMatch(
                new SpecFile("gateway", "/openspec/specs/gateway/spec.md"),
                new Requirement("Friendly greeting"));

        JLabel cell = (JLabel) renderer().getListCellRendererComponent(
                new JList<>(), match, 0, false, false);

        assertEquals("row text is the requirement name", "Friendly greeting", cell.getText());
        assertEquals("tooltip is the owning capability (domain)", "gateway", cell.getToolTipText());
    }

    public void testRendererToleratesNullValueWithoutThrowing() {
        // Search Everywhere renders null cells during list churn; the `if (value != null)` guard is
        // what keeps that from calling presentableText()/locationText() on null. Drop the guard and
        // this render NPEs — so a non-null result here proves the guard is intact.
        Component cell = renderer().getListCellRendererComponent(
                new JList<>(), null, 0, false, false);

        assertNotNull("a null value must render without throwing", cell);
    }

    private ListCellRenderer<? super SpecRequirementMatch> renderer() {
        // The contributor's constructor only stores the project, and getElementsRenderer() touches no
        // service — so this is cheap and needs no seeded SpecParsingService.
        return new SpecSearchEverywhereContributor(getProject()).getElementsRenderer();
    }
}
