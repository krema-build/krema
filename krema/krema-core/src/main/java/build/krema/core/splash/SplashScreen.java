package build.krema.core.splash;

import javax.imageio.ImageIO;
import javax.swing.*;

import build.krema.core.util.Logger;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Splash screen displayed during application startup.
 * Supports both the JDK SplashScreen API and a custom Swing-based fallback.
 */
public class SplashScreen implements AutoCloseable {

    private static final Logger LOG = new Logger("SplashScreen");

    private final JWindow window;
    private final JLabel imageLabel;
    private final JProgressBar progressBar;
    private final JLabel statusLabel;
    private final SplashScreenOptions options;

    /**
     * Creates a splash screen with the specified options.
     */
    public SplashScreen(SplashScreenOptions options) {
        this.options = options;
        this.window = new JWindow();

        // Main panel with image
        JPanel mainPanel = new JPanel(new BorderLayout());
        mainPanel.setBackground(options.backgroundColor());

        // Load and display image
        BufferedImage image = loadImage(options);
        if (image != null) {
            imageLabel = new JLabel(new ImageIcon(image));
        } else {
            imageLabel = new JLabel(createDefaultSplash(options));
        }
        mainPanel.add(imageLabel, BorderLayout.CENTER);

        // Bottom panel with progress and status
        JPanel bottomPanel = new JPanel(new BorderLayout(0, 5));
        bottomPanel.setOpaque(false);
        bottomPanel.setBorder(BorderFactory.createEmptyBorder(10, 20, 20, 20));

        if (options.showProgress()) {
            progressBar = new JProgressBar(0, 100);
            progressBar.setStringPainted(false);
            progressBar.setPreferredSize(new Dimension(0, 4));
            progressBar.setBorderPainted(false);
            progressBar.setForeground(options.progressColor());
            progressBar.setBackground(new Color(255, 255, 255, 50));
            bottomPanel.add(progressBar, BorderLayout.NORTH);
        } else {
            progressBar = null;
        }

        if (options.showStatus()) {
            statusLabel = new JLabel("Loading...");
            statusLabel.setForeground(options.textColor());
            statusLabel.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
            bottomPanel.add(statusLabel, BorderLayout.SOUTH);
        } else {
            statusLabel = null;
        }

        mainPanel.add(bottomPanel, BorderLayout.SOUTH);

        window.setContentPane(mainPanel);
        window.pack();

        // Center on screen
        Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
        int x = (screenSize.width - window.getWidth()) / 2;
        int y = (screenSize.height - window.getHeight()) / 2;
        window.setLocation(x, y);

        // Make it always on top during loading
        window.setAlwaysOnTop(true);
    }

    /**
     * Shows the splash screen.
     */
    public void show() {
        SwingUtilities.invokeLater(() -> {
            window.setVisible(true);
            LOG.debug("Splash screen shown");
        });
    }

    /**
     * Updates the progress bar (0-100).
     */
    public void setProgress(int progress) {
        if (progressBar != null) {
            SwingUtilities.invokeLater(() -> progressBar.setValue(progress));
        }
    }

    /**
     * Updates the status text.
     */
    public void setStatus(String status) {
        if (statusLabel != null) {
            SwingUtilities.invokeLater(() -> statusLabel.setText(status));
        }
    }

    /**
     * Hides the splash screen with an optional fade-out animation.
     */
    public void hide() {
        if (options.fadeOut()) {
            fadeOut();
        } else {
            close();
        }
    }

    private void fadeOut() {
        Timer timer = new Timer(30, null);
        final float[] opacity = {1.0f};

        timer.addActionListener(e -> {
            opacity[0] -= 0.1f;
            if (opacity[0] <= 0) {
                timer.stop();
                close();
            } else {
                window.setOpacity(opacity[0]);
            }
        });

        timer.start();
    }

    @Override
    public void close() {
        SwingUtilities.invokeLater(() -> {
            window.setVisible(false);
            window.dispose();
            LOG.debug("Splash screen closed");
        });
    }

    private BufferedImage loadImage(SplashScreenOptions options) {
        String imagePath = options.imagePath();
        if (imagePath == null || imagePath.isBlank()) {
            return null;
        }

        try {
            // Try as file path first
            Path path = Path.of(imagePath);
            if (Files.exists(path)) {
                return ImageIO.read(path.toFile());
            }

            // Try as classpath resource
            try (InputStream is = getClass().getResourceAsStream(imagePath)) {
                if (is != null) {
                    return ImageIO.read(is);
                }
            }

            // Try without leading slash
            if (!imagePath.startsWith("/")) {
                try (InputStream is = getClass().getResourceAsStream("/" + imagePath)) {
                    if (is != null) {
                        return ImageIO.read(is);
                    }
                }
            }

            LOG.warn("Splash image not found: %s", imagePath);
            return null;
        } catch (IOException e) {
            LOG.error("Failed to load splash image: %s", imagePath);
            return null;
        }
    }

    private ImageIcon createDefaultSplash(SplashScreenOptions options) {
        int width = options.width();
        int height = options.height();

        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2d = image.createGraphics();

        // Enable anti-aliasing
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        // Background gradient
        GradientPaint gradient = new GradientPaint(
            0, 0, new Color(102, 126, 234),
            width, height, new Color(118, 75, 162)
        );
        g2d.setPaint(gradient);
        g2d.fillRect(0, 0, width, height);

        // App name
        String appName = options.appName() != null ? options.appName() : "Krema";
        g2d.setColor(Color.WHITE);
        g2d.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 48));
        FontMetrics fm = g2d.getFontMetrics();
        int textWidth = fm.stringWidth(appName);
        int textX = (width - textWidth) / 2;
        int textY = height / 2;
        g2d.drawString(appName, textX, textY);

        // Version
        if (options.version() != null) {
            g2d.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 16));
            fm = g2d.getFontMetrics();
            String version = "v" + options.version();
            textWidth = fm.stringWidth(version);
            textX = (width - textWidth) / 2;
            g2d.drawString(version, textX, textY + 40);
        }

        g2d.dispose();
        return new ImageIcon(image);
    }

    /**
     * Tries to use the JDK's built-in splash screen if available.
     */
    public static java.awt.SplashScreen getJdkSplashScreen() {
        try {
            return java.awt.SplashScreen.getSplashScreen();
        } catch (Exception | UnsatisfiedLinkError e) {
            return null;
        }
    }

    /**
     * Closes the JDK splash screen if it's showing.
     */
    public static void closeJdkSplashScreen() {
        java.awt.SplashScreen splash = getJdkSplashScreen();
        if (splash != null && splash.isVisible()) {
            splash.close();
        }
    }
}
