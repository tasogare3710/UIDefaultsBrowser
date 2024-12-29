import static java.awt.RenderingHints.*;

import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.font.TextLayout;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.System.Logger.Level;
import java.lang.reflect.Array;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.stream.Collectors;

import javax.imageio.ImageIO;
import javax.swing.Icon;
import javax.swing.InputMap;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JMenuItem;
import javax.swing.JRadioButton;
import javax.swing.Painter;
import javax.swing.SwingUtilities;
import javax.swing.UIDefaults;
import javax.swing.UIManager;
import javax.swing.border.Border;

public class UIDefaultsBrowser {
    private static final System.Logger LOG = System.getLogger(UIDefaultsBrowser.class.getName());

    private static final int PREVIEW_LARGE_WIDTH = 512;
    private static final int PREVIEW_LARGE_HEIGHT = PREVIEW_LARGE_WIDTH;

    private int IMAGE_COUNT = 0;

    private static boolean isPreviewLarge(int width, int height) {
        return width > PREVIEW_LARGE_WIDTH || PREVIEW_LARGE_HEIGHT > 512;
    }

    public static final void main(String... args) {
        if (args.length != 1) {
            System.out.println(
                    """
                    Usage:
                        java UIDefaultsBrowser.java [output_dir]
                    """);
        } else {
            SwingUtilities.invokeLater(() -> new UIDefaultsBrowser(args[0]));
        }
    }

    public UIDefaultsBrowser(String output) {
        var selectedLookAndFeel =
                Objects.requireNonNull(UIManager.getLookAndFeel(), "disallow null-laf");
        var selectedLookAndFeelClassName = selectedLookAndFeel.getClass().getName();
        Arrays.stream(UIManager.getInstalledLookAndFeels())
                .map(UIManager.LookAndFeelInfo::getClassName)
                .forEach(
                        className -> {
                            if (className.equals(selectedLookAndFeelClassName)) {
                                System.out.println(className + " [SELECTED]");
                            } else {
                                System.out.println(className);
                            }
                        });
        System.out.println();
        var lookAndFeelDefaults = UIManager.getLookAndFeelDefaults();
        var componentDefaults =
                new TreeMap<String, Object>(
                        lookAndFeelDefaults.entrySet().stream()
                                .collect(
                                        Collectors.toMap(
                                                entry -> entry.getKey().toString(),
                                                Map.Entry::getValue)));
        // split out UIs
        var uiClasses =
                new TreeMap<String, Object>(
                        componentDefaults.entrySet().stream()
                                .filter(entry -> entry.getKey().endsWith("UI"))
                                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue)));
        for (var key : uiClasses.keySet()) {
            componentDefaults.remove(key);
        }

        // write html file
        var base = Paths.get(output, selectedLookAndFeelClassName);
        try {
            Files.createDirectories(base);
        } catch (IOException e) {
            e.printStackTrace();
        }

        var htmlFile = base.resolve("UIDefaults.html");
        System.out.println("Outputing to " + htmlFile.toAbsolutePath() + "\n");

        var titleComponents = "Components";
        var titleUIClasses = "UI Classes";
        var stringWriter = new StringWriter(200);
        try (PrintWriter html = new PrintWriter(stringWriter)) {
            html.println(
                    """
<!DOCTYPE html>
<html lang="en-US">
<head>
<style>
.generic_to_string {
    word-break: break-word;
}
.no-break-space {
    white-space: pre;
}
</style>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width">
<title>UIDefaults browser - browsing UIDefaults of %1$s</title>
</head>
<body>
<hgroup id="title">
<h1>UIDefaults browser</h1>
<p>browsing UIDefaults of <code>%1$s</code>.</p>
</hgroup>

<header>
<figure>
<figcaption>Table of contents</figcaption>

%2$s

</figure>
</header>
<main>

%3$s

%4$s

</main>
<footer id="footer">
<p>If the value is <mark>UIDefaults.ActiveValue</mark> or <mark>UIDefaults.LazyValue</mark>, they are marked.</p>
</footer>
</body>
</html>
"""
                            .formatted(
                                    selectedLookAndFeelClassName,
                                    tableOfContent(
                                            titleComponents, titleUIClasses, new StringBuilder()),
                                    table(
                                            titleComponents,
                                            componentDefaults,
                                            new StringBuilder(),
                                            base,
                                            lookAndFeelDefaults),
                                    table(
                                            titleUIClasses,
                                            uiClasses,
                                            new StringBuilder(),
                                            base,
                                            lookAndFeelDefaults)));

            Files.write(
                    htmlFile,
                    java.util.Arrays.asList(stringWriter.getBuffer()),
                    StandardCharsets.UTF_8);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private StringBuilder tableOfContent(
            String titleComponents, String titleUIClasses, StringBuilder html) throws Exception {
        return html.append("<ul>")
                .append("<li><a href=\"#" + titleComponents + "\">" + titleComponents + "</a></li>")
                .append("<li><a href=\"#" + titleUIClasses + "\">" + titleUIClasses + "</a>")
                .append("<li><a href=\"#footer\">Note</a>")
                .append("</ul>");
    }

    private StringBuilder table(
            String caption,
            Map<String, Object> map,
            StringBuilder html,
            Path base,
            UIDefaults table)
            throws Exception {
        html.append(
                """
                <table><caption id="%s">%s</caption>
                <thead><tr><th>Key</th><th>Value</th><th>Preview</th></tr></thead>
                <tbody>
                """
                        .formatted(caption, caption));
        for (var key : map.keySet()) {
            row(base, html, key, map.get(key), table);
        }
        return html.append("</tbody></table>");
    }

    private void row(Path base, StringBuilder html, String key, Object value, UIDefaults table)
            throws Exception {
        html.append("<tr><td><code>%s</code></td>".formatted(key));
        if (value instanceof UIDefaults.ActiveValue activeValue) {
            var live = activeValue.createValue(table);
            html.append(
                    "<td><mark>%s</mark></td><td class=\"no-break-space\">&#x20;</td>"
                            .formatted(activeValue));
            row(base, html, activeValue.toString(), live, table);
        } else if (value instanceof UIDefaults.LazyValue lazyValue) {
            var live = lazyValue.createValue(table);
            html.append(
                    "<td><mark>%s</mark></td><td class=\"no-break-space\">&#x20;</td>"
                            .formatted(lazyValue));
            row(base, html, lazyValue.toString(), live, table);
        } else if (value instanceof Color color) {
            this.color(html, color);
        } else if (value instanceof Font font) {
            this.font(base, html, font);
        } else if (value instanceof Dimension dim) {
            this.dimension(base, html, dim);
        } else if (value instanceof Insets insets) {
            this.insets(base, html, insets);
        } else if (value instanceof Border border) {
            this.border(base, html, border);
        } else if (value instanceof Painter painter) {
            this.painter(base, html, painter);
        } else if (value instanceof InputMap inputMap) {
            this.inputMap(html, inputMap);
        } else if (value instanceof Icon icon) {
            this.icon(base, html, icon);
        } else if (value instanceof Number number) {
            var s = Objects.toString(number);
            html.append("<td>%s</td><td class=\"no-break-space\">&#x20;</td>".formatted(s));
        } else if (value instanceof Boolean b) {
            var s = Objects.toString(b);
            html.append("<td>%s</td><td class=\"no-break-space\">&#x20;</td>".formatted(s));
        } else if (value != null && value.getClass().isArray()) {
            this.array(html, value);
        } else {
            var s = Objects.toString(value);
            html.append(
                    """
                    <td class="generic_to_string">%s</td><td class=\"no-break-space\">&#x20;</td>
                    """
                            .formatted(s));
        }
        html.append("</tr>");
    }

    private void color(StringBuilder html, Color color) {
        var webColor = getWebColor(color);
        var negativeColor = getNegativeWebColor(color);
        var colorTuple = getColorTuple(color);
        html.append(
                """
                <td><code>%1$s</code></td>
                <td title="#%3$s" style="background-color: #%3$s;color: #%4$s;">#%3$s</td>
                """
                        .formatted(color, colorTuple, webColor, negativeColor));
    }

    @SuppressWarnings("unchecked")
    private void painter(Path base, StringBuilder html, Painter painter) throws Exception {
        html.append("<td>%s</td>".formatted(painter.getClass().getTypeName()));
        int w = 25;
        int h = 25;
        var img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        var g2 = img.createGraphics();
        var old = g2.getComposite();
        g2.setComposite(AlphaComposite.Clear);
        g2.fillRect(0, 0, w, h);
        g2.setComposite(old);
        boolean skipPaint = false;
        try {
            painter.paint(g2, makeJComponent(w, h), w, h);
        } catch (Exception e) {
            LOG.log(Level.ERROR, "skip paint painter");
            g2.drawString("skip paint", 0, 0);
            html.append("<td class=\"no-break-space\">&#x20;</td>");
        }
        g2.dispose();
        html.append("<td>%s</td>".formatted(saveImage(base, img, skipPaint)));
    }

    private void font(Path base, StringBuilder html, Font font) throws Exception {
        int w = 320;
        int h = font.getSize() * 2;
        var img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        var g2 = img.createGraphics();
        g2.addRenderingHints(Map.of(KEY_TEXT_ANTIALIASING, VALUE_TEXT_ANTIALIAS_ON));
        var old = g2.getComposite();
        g2.setComposite(AlphaComposite.Clear);
        var frc = g2.getFontRenderContext();
        var layout = new TextLayout("the quick brown fox jumps over the crazy dog", font, frc);
        float x = ((float) w) / 2.0f - layout.getAdvance() / 2.0f;
        float y = layout.getAscent() + layout.getDescent() + layout.getLeading();
        var bounds = layout.getBounds();
        bounds.setRect(
                bounds.getX() + (double) x,
                bounds.getY() + (double) y,
                bounds.getWidth(),
                bounds.getHeight());
        g2.draw(bounds);
        g2.setComposite(old);
        g2.setColor(Color.BLACK);
        layout.draw(g2, x, y);
        g2.dispose();

        html.append("<td>%s</td><td>%s</td>".formatted(font, saveImage(base, img, false)));
    }

    private void insets(Path base, StringBuilder html, Insets insets) throws Exception {
        int w = 50 + insets.left + insets.right;
        int h = 20 + insets.top + insets.bottom;
        var img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        var g2 = img.createGraphics();
        var old = g2.getComposite();
        g2.setComposite(AlphaComposite.Clear);
        g2.fillRect(0, 0, w, h);
        g2.setComposite(old);
        g2.setColor(Color.BLACK);
        g2.drawRect(insets.left, insets.top, 49, 19);
        g2.setColor(Color.RED);
        g2.drawRect(0, 0, w - 1, h - 1);
        g2.dispose();

        html.append("<td>%s</td><td>%s</td>".formatted(insets, saveImage(base, img, false)));
    }

    private void border(Path base, StringBuilder html, Border border) throws Exception {
        var insets = border.getBorderInsets(null);
        html.append("<td>%s</td>".formatted(insets));
        int w = 50 + insets.left + insets.right;
        int h = 20 + insets.top + insets.bottom;
        try {
            var img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
            var g2 = img.createGraphics();
            var old = g2.getComposite();
            g2.setComposite(AlphaComposite.Clear);
            g2.fillRect(0, 0, w, h);
            g2.setComposite(old);
            g2.setColor(Color.RED);
            g2.fillRect(insets.left, insets.top, 49, 19);
            boolean skipPaint = false;
            try {
                // FIXME: borderの種類ごとにcomponentを渡す必要がある
                border.paintBorder(makeJComponent(w, h), g2, 0, 0, w, h);
            } catch (Exception e) {
                try {
                    var b = new JButton();
                    b.setBorder(border);
                    b.setPreferredSize(new Dimension(w, h));
                    border.paintBorder(b, g2, 0, 0, w, h);
                } catch (Exception e1) {
                    LOG.log(Level.ERROR, e1.getMessage());
                    g2.drawString("skip paint", 0, 0);
                    skipPaint = true;
                }
            }
            g2.dispose();
            html.append("<td>%s</td>".formatted(saveImage(base, img, skipPaint)));
        } catch (Exception e) {
            e.printStackTrace();
            html.append("<td class=\"no-break-space\">&#x20;</td>");
        }
    }

    private void dimension(Path base, StringBuilder html, Dimension dim) throws Exception {
        html.append("<td>%s</td>".formatted(dim));
        int w = dim.width;
        int h = dim.height;
        if (w == 0 || h == 0) {
            html.append("<td class=\"no-break-space\">&#x20;</td>");
        } else {
            var img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
            var g2 = img.createGraphics();
            var old = g2.getComposite();
            g2.setComposite(AlphaComposite.Clear);
            g2.fillRect(0, 0, w, h);
            g2.setComposite(old);
            g2.setColor(Color.RED);
            g2.drawRect(0, 0, w - 1, h - 1);
            g2.dispose();
            html.append("<td>%s</td>".formatted(saveImage(base, img, false)));
        }
    }

    private boolean printIconImpl(
            Graphics2D g2, Icon icon, JComponent component, int width, int height)
            throws Exception {
        try {
            icon.paintIcon(component, g2, 0, 0);
            return false;
        } catch (Exception e) {
            try {
                var cb = new JCheckBox(icon);
                cb.setPreferredSize(new Dimension(width, height));
                icon.paintIcon(cb, g2, 0, 0);
                return false;
            } catch (Exception e2) {
                try {
                    var cb = new JComboBox();
                    cb.setPreferredSize(new Dimension(width, height));
                    icon.paintIcon(cb, g2, 0, 0);
                    return false;
                } catch (Exception e3) {
                    try {
                        var mi = new JMenuItem(icon);
                        icon.paintIcon(mi, g2, 0, 0);
                        return false;
                    } catch (Exception e4) {
                        try {
                            var rb = new JRadioButton(icon);
                            rb.setPreferredSize(new Dimension(width, height));
                            icon.paintIcon(rb, g2, 0, 0);
                            return false;
                        } catch (Exception e5) {
                        }
                    }
                }
            }
            LOG.log(Level.ERROR, e.getMessage());
            g2.drawString("skip paint", 0, 0);
            return true;
        }
    }

    private void icon(Path base, StringBuilder html, Icon icon) throws Exception {
        int w = icon.getIconWidth();
        if (w <= 0) {
            w = 16;
            LOG.log(Level.INFO, "icon width <= 0, so changed image width to 16");
        }

        int h = icon.getIconHeight();
        if (h <= 0) {
            h = 16;
            LOG.log(Level.INFO, "icon height <= 0, so changed image width to 16");
        }

        var img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        var g2 = img.createGraphics();
        var old = g2.getComposite();
        g2.setComposite(AlphaComposite.Clear);
        g2.fillRect(0, 0, w, h);
        g2.setComposite(old);
        boolean skipPaint = printIconImpl(g2, icon, makeJComponent(w, h), w, h);
        g2.dispose();

        html.append(
                "<td>Icon %s * %s</td><td>%s</td>"
                        .formatted(
                                icon.getIconWidth(),
                                icon.getIconHeight(),
                                saveImage(base, img, skipPaint)));
    }

    private void inputMap(StringBuilder html, InputMap inputMap) {
        html.append(
                """
                <td>
                <details>
                <summary>%s</summary>
                <ul>
                """
                        .formatted(inputMap.getClass().getTypeName()));
        for (var key : inputMap.allKeys()) {
            var binding = inputMap.get(key);
            html.append("<li><code>%s</code> : %s".formatted(key, binding));
        }
        html.append(
                """
                </ul>
                </details>
                </td>
                <td class=\"no-break-space\">&#x20;</td>
                """);
    }

    private void array(StringBuilder html, Object value) {
        html.append(
                """
                <td>
                <details>
                <summary>%s</summary>
                <ul>
                """
                        .formatted(value.getClass().getTypeName()));
        for (var i = 0; i < Array.getLength(value); i++) {
            var c = Array.get(value, i);
            html.append("<li>").append(c).append("</li>");
        }
        html.append(
                """
                </ul>
                </details>
                </td>
                <td class=\"no-break-space\">&#x20;</td>
                """);
    }

    private String saveImage(Path base, BufferedImage img, boolean skipPaint) throws Exception {
        var path = base.resolve("images");
        Files.createDirectories(path);
        var name = "img_" + (IMAGE_COUNT++) + ".png";
        path = path.resolve(name);
        if (Files.notExists(path)) {
            Files.createFile(path);
        }
        var imgFile = path.toFile();
        ImageIO.write(img, "png", imgFile);

        int width = img.getWidth();
        int height = img.getHeight();
        if (skipPaint) {
            return """
               <img src="%s" alt="%s"><strong>skip paint</strong>
            """
                    .formatted(base.relativize(path), name);
        } else if (isPreviewLarge(width, height)) {
            LOG.log(
                    Level.INFO,
                    """
                    large preview converted to link: %s * %s
                    """
                            .formatted(width, height));
            return """
            <a href="%s" title="%s">show large preview</a>
            """
                    .formatted(base.relativize(path), name);
        } else {
            return """
               <img src="%s" alt="%s">
            """
                    .formatted(base.relativize(path), name);
        }
    }

    private String getColorTuple(Color color) {
        return color.getRed()
                + ","
                + color.getGreen()
                + ","
                + color.getBlue()
                + ","
                + color.getAlpha();
    }

    private String getWebColor(Color color) {
        return getWebColor(color.getRed(), color.getGreen(), color.getBlue(), color.getAlpha());
    }

    private String getWebColor(int red, int green, int blue, int alpha) {
        var result = new StringBuilder();

        var num = Integer.toHexString(red);
        if (num.length() == 1) {
            num = "0" + num;
        }
        result.append(num);

        num = Integer.toHexString(green);
        if (num.length() == 1) {
            num = "0" + num;
        }
        result.append(num);

        num = Integer.toHexString(blue);
        if (num.length() == 1) {
            num = "0" + num;
        }
        result.append(num);

        num = Integer.toHexString(alpha);
        if (num.length() == 1) {
            num = "0" + num;
        }
        result.append(num);

        return result.toString();
    }

    private String getNegativeWebColor(Color color) {
        return getWebColor(
                0xff - color.getRed(),
                0xff - color.getGreen(),
                0xff - color.getBlue(),
                color.getAlpha());
    }

    private JComponent makeJComponent(int w, int h) {
        return new JComponent() {
            @Override
            public int getWidth() {
                return w;
            }

            @Override
            public int getHeight() {
                return h;
            }

            @Override
            public Dimension getSize(Dimension rv) {
                return new Dimension(getWidth(), getHeight());
            }
        };
    }
}
