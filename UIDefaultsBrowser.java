import javax.imageio.ImageIO;
import javax.swing.Icon;
import javax.swing.InputMap;
import javax.swing.SwingUtilities;
import javax.swing.UIDefaults;
import javax.swing.UIManager;
import javax.swing.border.Border;
import javax.swing.Painter;
import java.lang.reflect.Array;
import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Composite;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.StringWriter;
import java.io.PrintWriter;
import java.nio.file.Paths;
import java.nio.file.Path;
import java.nio.file.Files;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Objects;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class UIDefaultsBrowser {
    private static final Set<String> NIMBUS_PRIMARY_COLORS = Set.of(
        "text", "control", "nimbusBase", "nimbusOrange", "nimbusGreen", "nimbusRed", "nimbusInfoBlue",
        "nimbusAlertYellow", "nimbusFocus", "nimbusSelectedText", "nimbusSelectionBackground",
        "nimbusDisabledText", "nimbusLightBackground", "info");
    private static final Set<String> NIMBUS_SECONDARY_COLORS = Set.of(
        "textForeground", "textBackground", "background", "nimbusBlueGrey", "nimbusBorder",
        "nimbusSelection", "infoText", "menuText", "menu", "scrollbar", "controlText",
        "controlHighlight", "controlLHighlight", "controlShadow", "controlDkShadow", "textHighlight",
        "textHighlightText", "textInactiveText", "desktop", "activeCaption", "inactiveCaption");
    private static final List<String> NIMBUS_COMPONENTS = List.of(
        "ArrowButton", "Button", "ToggleButton", "RadioButton", "CheckBox", "ColorChooser", "ComboBox",
        "\"ComboBox.scrollPane\"", "FileChooser", "InternalFrameTitlePane", "InternalFrame", "DesktopIcon",
        "DesktopPane", "Label", "List", "MenuBar", "MenuItem", "RadioButtonMenuItem", "CheckBoxMenuItem", "Menu",
        "PopupMenu", "PopupMenuSeparator", "OptionPane", "Panel", "ProgressBar", "Separator", "ScrollBar",
        "ScrollPane", "Viewport", "Slider", "Spinner", "SplitPane", "TabbedPane", "Table", "TableHeader",
        "\"Table.editor\"", "\"Tree.cellEditor\"", "TextField", "FormattedTextField", "PasswordField", "TextArea",
        "TextPane", "EditorPane", "ToolBar", "ToolBarSeparator", "ToolTip", "Tree", "RootPane");

    private int IMAGE_COUNT = 0;

    public static final void main(String... args) {
        SwingUtilities.invokeLater(new UIDefaultsBrowser()::run);
    }

    private void run() {
        var nimbusLookAndFeelClassName = "";
        for (var laf : UIManager.getInstalledLookAndFeels()) {
            if (laf.getName().contains("Nimbus")) {
                try {
                    nimbusLookAndFeelClassName = laf.getClassName();
                    UIManager.setLookAndFeel(nimbusLookAndFeelClassName);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }

        var defaults = UIManager.getLookAndFeelDefaults();
        var componentDefaults = new HashMap<String, Map<String, Object>>();
        var others = new HashMap<String, Object>();
        for (var key : defaults.keySet().parallelStream().map(Object::toString).toList()) {
            boolean matchesComponent = false;
            componentloop: for (var componentName : NIMBUS_COMPONENTS) {
                if (key.startsWith(componentName + ".") ||
                    key.startsWith(componentName + ":") ||
                    key.startsWith(componentName + "["))
                {
                    var keys = componentDefaults.get(componentName);
                    if (keys == null) {
                        keys = new HashMap<String, Object>();
                        componentDefaults.put(componentName, keys);
                    }
                    keys.put(key, defaults.get(key));
                    matchesComponent = true;
                    break componentloop;
                }
            }
            if (!matchesComponent) { others.put(key, defaults.get(key)); }
        }
        // split out primary, secondary colors
        var primaryColors = new HashMap<String, Object>();
        var secondaryColors = new HashMap<String, Object>();
        for (var entry : others.entrySet()) {
            if (NIMBUS_PRIMARY_COLORS.contains(entry.getKey())) {
                primaryColors.put(entry.getKey(), (Color) entry.getValue());
            }
            if (NIMBUS_SECONDARY_COLORS.contains(entry.getKey())) {
                secondaryColors.put(entry.getKey(), (Color) entry.getValue());
            }
        }
        for (var key : NIMBUS_PRIMARY_COLORS) {
            others.remove(NIMBUS_PRIMARY_COLORS);
        }
        for (var key : NIMBUS_SECONDARY_COLORS) {
            others.remove(key);
        }
        // split out UIs
        var uiClasses = new HashMap<String, Object>();
        for (var entry : others.entrySet()) {
            if (entry.getKey().endsWith("UI")) {
                uiClasses.put(entry.getKey(), entry.getValue());
            }
        }
        for (var key : uiClasses.keySet()) {
            others.remove(key);
        }
        // write html file
        var base = Paths.get("output");
        try {
            Files.createDirectories(base);
        } catch (IOException e) {
            e.printStackTrace();
        }

        var htmlFile = base.resolve("nimbus.html");
        System.out.println("Outputing to " + htmlFile.toAbsolutePath());
        var stringWriter = new StringWriter(200);
        try (var html = new PrintWriter(stringWriter)) {
            html.println("""
            <!DOCTYPE html>
            <html lang="en-US">
            <head>
            <meta charset="utf-8">
            <meta name="viewport" content="width=device-width">
            <title>UIDefaults browser</title>
            <link href="style.css" rel="stylesheet" type="text/css"/>
            </head>
            <body>
            <hgroup id="title">
            <h1>UIDefaults browser</h1>
            <p>browsing UIDefaults.</p>
            </hgroup>
            """);

            var titlePrimaryColors = "Primary Colors";
            var titleSecondaryColors = "Secondary Colors";
            var titleComponents="Components";
            var titleOthers = "Others";
            var titleUIClasses = "UI Classes";
            html.println("""
            <header>
            <figure>
            <figcaption>%s</figcaption>
            """.formatted(nimbusLookAndFeelClassName));

            printTableOfContent(
                titlePrimaryColors,
                titleSecondaryColors,
                titleComponents,
                titleOthers,
                titleUIClasses,
                html,
                componentDefaults
            );

            html.println("""
            </figure>
            </header>
            <main>
            """);

            printTable(titlePrimaryColors, base, html, primaryColors);
            printTable(titleSecondaryColors, base, html, secondaryColors);

            html.println("""
            <header id="%s">%s</header>
            """.formatted(titleComponents, titleComponents));
            for (var entry : componentDefaults.entrySet()) {
                var titleKey = titleComponents + " - " + entry.getKey();
                printTable(titleKey, base, html, entry.getValue());
            }

            printTable(titleOthers, base, html, others);
            printTable(titleUIClasses , base, html, uiClasses);

            html.println("""
            </main>
            </body>
            </html>
            """);

            Files.write(htmlFile, java.util.Arrays.asList(stringWriter.getBuffer()), StandardCharsets.UTF_8);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void printTableOfContent(
        String titlePrimaryColors,
        String titleSecondaryColors,
        String titleComponents,
        String titleOthers,
        String titleUIClasses,
        PrintWriter html,
        HashMap<String, Map<String, Object>> componentDefaults
    ) throws Exception {
        html.println("<ul>");
        html.println("<li><a href=\"#" + titlePrimaryColors + "\">" + titlePrimaryColors + "</a>");
        html.println("<li><a href=\"#" + titleSecondaryColors + "\">" + titleSecondaryColors + "</a>");
        html.println("<li><a href=\"#" + titleComponents + "\">" + titleComponents + "</a>");
        html.println("<ul>");
        for (var entry : componentDefaults.entrySet()) {
            var titleKey = titleComponents + " - " + entry.getKey();
            html.println("<li><a href=\"#" + titleKey + "\">" + titleKey + "</a></li>");
        }
        html.println("</ul>");
        html.println("</li>");
        html.println("<li><a href=\"#" + titleOthers + "\">" + titleOthers + "</a>");
        html.println("<li><a href=\"#" + titleUIClasses + "\">" + titleUIClasses + "</a>");
        html.println("</ul>");
    }

    private void printTable(String caption, Path base, PrintWriter html, Map<String, Object> map) throws Exception {
        html.println("""
        <table><caption id="%s">%s</caption>
        <thead><tr><th>Key</th><th>Value</th><th>Preview</th></tr></thead>
        <tbody>
        """.formatted(caption, caption));
        for (var key : map.keySet().stream().sorted().toList()) {
            printRow(base, html, key, map.get(key));
        }
        html.println("</tbody></table>");
    }

    private void printRow(Path base, PrintWriter html, String key, Object value) throws Exception {
        html.println("<tr><td><code>%s</code></td>".formatted(key));
        if (value instanceof Color color) {
            printColor(html, color);
        } else if (value instanceof Font font) {
            printFont(base, html, font);
        } else if (value instanceof Dimension dim) {
            printDimension(base, html, dim);
        } else if (value instanceof Insets insets) {
            printInsets(base, html, insets);
        } else if (value instanceof Border border) {
            printBorder(base, html, border);
        } else if (value instanceof Painter painter) {
            printPainter(base, html, painter);
        } else if (value instanceof InputMap inputMap) {
            printInputMap(html, inputMap);
        } else if (value instanceof Icon icon) {
            printIcon(base, html, icon);
        } else if (value != null && value.getClass().isArray()) {
            printArray(html, value);
        } else {
            var s=Objects.toString(value);
            html.println("<td>%s</td><td>&nbsp;</td>".formatted(s));
        }
        html.println("</tr>");
    }

    private void printColor(PrintWriter html, Color color) {
        var webColor = getWebColor(color);
        var colorTuple = getColorTuple(color);
        html.println("""
        <td><code title="%s">#%s</code></td>
        <td style="background-color: #%s;"></td>
        """.formatted(colorTuple, webColor, webColor));
    }

    private void printPainter(Path base, PrintWriter html, Painter painter) throws Exception {
        html.println("<td>%s</td>".formatted(painter.getClass().getTypeName()));
        int w=25,h=25;
        try {
            var img = new BufferedImage(w,h, BufferedImage.TYPE_INT_ARGB);
            var g2 = img.createGraphics();
            var old = g2.getComposite();
            g2.setComposite(AlphaComposite.Clear);
            g2.fillRect(0,0,w,h);
            g2.setComposite(old);
            painter.paint(g2, new javax.swing.JComponent(){},w,h);
            g2.dispose();
            html.println("<td>%s</td>".formatted(saveImage(base, img)));
        } catch (Exception e) {
            e.printStackTrace();
            html.println("<td>&nbsp;</td>");
        }
    }

    private void printFont(Path base, PrintWriter html, Font font) throws Exception {
        int w=300,h=30;
        var img = new BufferedImage(w,h, BufferedImage.TYPE_INT_ARGB);
        var g2 = img.createGraphics();
        var old = g2.getComposite();
        g2.setComposite(AlphaComposite.Clear);
        g2.fillRect(0,0,w,h);
        g2.setComposite(old);
        g2.setColor(Color.BLACK);
        g2.setFont(font);
        g2.drawString("the quick brown fox jumps over the crazy dog",5,20);
        g2.dispose();

        html.println("<td>%s</td><td>%s</td>".formatted(font, saveImage(base, img)));
    }

    private void printInsets(Path base, PrintWriter html, Insets insets) throws Exception {
        int w = 50+insets.left+insets.right;
        int h = 20+insets.top+insets.bottom;
        var img = new BufferedImage(w,h, BufferedImage.TYPE_INT_ARGB);
        var g2 = img.createGraphics();
        var old = g2.getComposite();
        g2.setComposite(AlphaComposite.Clear);
        g2.fillRect(0,0,w,h);
        g2.setComposite(old);
        g2.setColor(Color.BLACK);
        g2.drawRect(insets.left, insets.top, 49,19);
        g2.setColor(Color.RED);
        g2.drawRect(0,0,w-1,h-1);
        g2.dispose();

        html.println("<td>%s</td><td>%s</td>".formatted(insets, saveImage(base, img)));
    }

    private void printBorder(Path base, PrintWriter html, Border border) throws Exception {
        var insets = border.getBorderInsets(null);
        html.println("<td>%s</td>".formatted(insets));
        int w = 50+insets.left+insets.right;
        int h = 20+insets.top+insets.bottom;
        try {
            var img = new BufferedImage(w,h, BufferedImage.TYPE_INT_ARGB);
            var g2 = img.createGraphics();
            var old = g2.getComposite();
            g2.setComposite(AlphaComposite.Clear);
            g2.fillRect(0,0,w,h);
            g2.setComposite(old);
            g2.setColor(Color.RED);
            g2.fillRect(insets.left, insets.top, 49,19);
            border.paintBorder(null,g2, 0,0,w,h);
            g2.dispose();
            html.println("<td>%s</td>".formatted(saveImage(base, img)));
        } catch (Exception e) {
            e.printStackTrace();
            html.println("<td>&nbsp;</td>");
        }
    }

    private void printDimension(Path base, PrintWriter html, Dimension dim) throws Exception {
        html.println("<td>%s</td>".formatted(dim));
        int w = dim.width;
        int h = dim.height;
        if (w==0 || h==0){
            html.println("<td>&nbsp;</td>");
        } else {
            var img = new BufferedImage(w,h, BufferedImage.TYPE_INT_ARGB);
            var g2 = img.createGraphics();
            var old = g2.getComposite();
            g2.setComposite(AlphaComposite.Clear);
            g2.fillRect(0,0,w,h);
            g2.setComposite(old);
            g2.setColor(Color.RED);
            g2.drawRect(0,0,w-1,h-1);
            g2.dispose();
            html.println("<td>%s</td>".formatted(saveImage(base, img)));
        }
    }

    private void printIcon(Path base, PrintWriter html, Icon icon) throws Exception {
        var img = new BufferedImage(icon.getIconWidth(), icon.getIconHeight(), BufferedImage.TYPE_INT_ARGB);
        var g2 = img.createGraphics();
        var old = g2.getComposite();
        g2.setComposite(AlphaComposite.Clear);
        g2.fillRect(0,0,icon.getIconWidth(), icon.getIconHeight());
        g2.setComposite(old);
        icon.paintIcon(null,g2, 0, 0);
        g2.dispose();

        html.println("""
        <td>Icon %s * %s</td><td>%s</td>
        """.formatted(icon.getIconWidth(), icon.getIconHeight(), saveImage(base, img)));
    }

    private void printInputMap(PrintWriter html, InputMap inputMap) {
        html.println("""
        <td>
        <details>
        <summary>%s</summary>
        <ul>
        """.formatted(inputMap.getClass().getTypeName()));
        for (var key : inputMap.allKeys()){
            var binding = inputMap.get(key);
            html.println("""
            <li><code>%s</code> : %s
            """.formatted(key, binding));
        }
        html.println("""
        </ul>
        </details>
        </td>
        <td>&nbsp;</td>
        """);
    }

    private void printArray(PrintWriter html, Object value) {
        html.println("""
        <td>
        <details>
        <summary>%s</summary>
        <ul>
        """.formatted(value.getClass().getTypeName()));
        for(var i=0; i<Array.getLength​(value); i++){
            var c = Array.get(value, i);
            html.println("<li>"+ c);
        }
        html.println("""
        </ul>
        </details>
        </td>
        <td>&nbsp;</td>
        """);
    }

    private String saveImage(Path base, BufferedImage img) throws Exception {
        var path = base.resolve("images");
        Files.createDirectories(path);
        var name = "img_" + (IMAGE_COUNT++) + ".png";
        path = path.resolve(name);
        if(Files.notExists(path)) {
            Files.createFile(path);
        }
        var imgFile = path.toFile();
        ImageIO.write(img,"png",imgFile);
        return """
        <img src="%s" alt="%s">
        """.formatted(base.relativize(path), name);
    }

    private String getColorTuple(Color color) {
        return color.getRed() + "," + color.getGreen() + "," + color.getBlue() + "," + color.getAlpha();
    }

    private String getWebColor(Color color) {
        String result = "";

        String num = Integer.toHexString(color.getRed());
        if (num.length() == 1) { num = "0" + num; }
        result += num;

        num = Integer.toHexString(color.getGreen());
        if (num.length() == 1) { num = "0" + num; }
        result += num;

        num = Integer.toHexString(color.getBlue());
        if (num.length() == 1) { num = "0" + num; }
        result += num;

        num = Integer.toHexString(color.getAlpha());
        if (num.length() == 1) { num = "0" + num; }
        result += num;

        return result;
    }

}