package com.project.client;

import com.piomatter.PioMatter;
import com.piomatter.UtilsFPS;
import com.piomatter.UtilsImage;
import com.piomatter.UtilsImage.FitMode;

import org.json.JSONObject;
import org.json.JSONArray;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.Files;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

import javax.imageio.ImageIO;

public class Main {

    // Matriu
    private static final int WIDTH = 64, HEIGHT = 64;
    private static final int ADDR = 5;          // ABCDE
    private static final int LANES = 2;         // 2 lanes
    private static final int BRIGHTNESS = 100;  // 0..255
    private static final int FPS_CAP = 60;

    // Dibuix
    private static final int TEXT_X = 5;
    // Reservem una franja superior per a l'overlay d'FPS (~10-12px) + marge.
    private static final int RESERVED_TOP = 12;
    private static final int TEXT_TOP_PAD = 2; // separació extra respecte l'overlay

    // Variables para scroll horizontal
    private volatile int scrollX = 0;
    private volatile long lastScrollTime = 0;
    private volatile String scrollingText = null;

    // Estat missatge
    private enum Mode { NONE, TEXT, IMAGE }
    private volatile Mode mode = Mode.NONE;
    private volatile String  text = null;
    private volatile BufferedImage image = null;
    private volatile long expireAtMs = 0L;

    private volatile Boolean alreadyConfigured = false;

    private final UtilsWS ws;


    // Estat del joc
    private volatile boolean jocActiu = false;
    private volatile int j1Punts = 0;
    private volatile int j2Punts = 0;
    private volatile List<GameObject> gameObjects = new ArrayList<>();

    public Main(String serverUri) {
        ws = UtilsWS.getSharedInstance(serverUri);

        ws.onClose((reason) -> System.out.println("[client] WebSocket cerrado: " + reason));
        ws.onError((e) -> System.out.println("[client] WebSocket error: " + e));

        ws.onMessage(this::onWsMessage);
        ws.onOpen(this::onWsOpen);

    }

    private void onWsOpen(String msg) {
        if (alreadyConfigured) return;
        try {
            // Identificación como Raspberry
            JSONObject jsonObject = new JSONObject();
            jsonObject.put("type", "raspberry");
            jsonObject.put("message", "solicito_config");
            ws.safeSend(jsonObject.toString());
            System.out.println("[client] Solicitud de configuración enviada al servidor");
        } catch (Exception e) {
            System.out.println("[client] Error enviando solicitud: " + e.getMessage());
        }
    }


    private void onWsMessage(String msg) {
        try {
            JSONObject o = new JSONObject(msg);
            String t = o.optString("type", "");

            // Només si no és jocData
            if (!t.equals("jocData")) {
                long ttl = Math.max(1, o.optLong("ttl_ms", 5000L));
                expireAtMs = System.currentTimeMillis() + ttl;
            }
            

            switch (t) {
                case "text" -> {
                    text = o.optString("message", "");
                    image = null;
                    mode = Mode.TEXT;
                    System.out.println("[client] TEXT: " + text);
                }
                case "config" -> {
                    String groupName = o.optString("groupName", "groupName desconocido");
                    String url = o.optString("url", "url desconocida");

                    alreadyConfigured = true;

                    // Guardamos el texto y activamos el modo texto
                    text = "Grupo: " + groupName;
                    mode = Mode.TEXT;
                    scrollX = 0; // Reset scroll
                    scrollingText = null;

                    // Tiempo que queremos que se muestre (3 segundos)
                    expireAtMs = System.currentTimeMillis() + 3_000L;

                    System.out.println("[client] Nombre del grupo recibido: " + groupName);

                    // Programar la carga de la URL después de que termine el tiempo del grupo
                    new Thread(() -> {
                        try {
                            // Esperar exactamente el tiempo que se muestra el grupo + un pequeño margen
                            Thread.sleep(3_000L + 100L);
                            
                            // Cargar la URL desde el archivo JSON en la carpeta dades
                            if (url != null && !url.isEmpty()) {
                                // Actualizar el texto con la URL (en el hilo principal)
                                javax.swing.SwingUtilities.invokeLater(() -> {
                                    text = "URL: " + url;
                                    scrollingText = "URL: " + url; // Guardar texto completo para scroll
                                    scrollX = WIDTH; // Empezar desde la derecha
                                    lastScrollTime = System.currentTimeMillis();
                                    mode = Mode.TEXT;
                                    // Mostrar la URL por 10 segundos
                                    expireAtMs = System.currentTimeMillis() + 30_000L;
                                    System.out.println("[client] Mostrando URL con scroll: " + url);
                                });
                            } else {
                                System.out.println("[client] No se encontró URL en el archivo JSON");
                            }
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                    }).start();
                }
                case "countdown" -> {
                    int value = o.optInt("value", 3);
                    
                    if (value > 0) {
                        // Mostrar el número del countdown (3, 2, 1)
                        text = String.valueOf(value);
                        mode = Mode.TEXT;
                        System.out.println("[client] Countdown: " + value);
                    } else {
                        // Cuando value es 0, limpiar la pantalla
                        mode = Mode.NONE;
                        text = null;
                        System.out.println("[client] Countdown terminado");
                    }
                    image = null;
                }
                case "jocData" -> {
                    String estatPartida = o.optString("estatPartida","");

                    if (estatPartida.equals("Jugant")) {
                        jocActiu = true;

                        // Actualitzar punts
                        j1Punts = o.optInt("J1Punts", 0);
                        j2Punts = o.optInt("J2Punts", 0);

                        // Obtenir dades dels objectes
                        JSONArray objectsList = o.optJSONArray("objectsList");
                        if (objectsList != null) {

                            gameObjects.clear();

                            for (int i = 0; i < objectsList.length(); i++) {
                                JSONObject object = objectsList.getJSONObject(i);

                                // Escalem les coordenades i dimensions segons la mida de la pantalla
                                //GameObject gameObject = GameObject.fromJSON(object, WIDTH, HEIGHT);
                                GameObject gameObject = GameObject.fromJSONScaledToGameArea(
                                    object,
                                    WIDTH,
                                    HEIGHT,
                                    RESERVED_TOP,
                                    600,
                                    400
                                );

                                // Afegim l'objecte a la llista
                                gameObjects.add(gameObject);
                            }
                        }

                    } else {
                        jocActiu = false;
                        gameObjects.clear();
                    }
                }
                case "gameOver" -> {
                    String winner = o.optString("winner", "");
                    text = "WINNER: \n" + winner;
                    mode = Mode.TEXT;
                    image = null;
                    System.out.println("[client] GAME OVER! Guanyador: " + winner);
                }
                case "image" -> {
                    String b64 = o.optString("b64", "");
                    if (b64.isEmpty()) { mode = Mode.NONE; return; }
                    try {
                        byte[] data = Base64.getDecoder().decode(b64);
                        BufferedImage img = ImageIO.read(new ByteArrayInputStream(data));
                        if (img != null) {
                            image = img;
                            text = null;
                            mode = Mode.IMAGE;
                            System.out.println("[client] IMAGE: " + o.optString("name", "(unnamed)"));
                        } else {
                            System.out.println("[client] IMAGE decode failed.");
                            mode = Mode.NONE;
                        }
                    } catch (Exception e) {
                        System.out.println("[client] IMAGE error: " + e.getMessage());
                        mode = Mode.NONE;
                    }
                }
                default -> {
                    // ignore
                }
            }
        } catch (Exception ignored) {}
    }


    public static String loadUrlFromJson() {
        try {
            // Usar la ruta exacta donde se encontró el archivo
            Path jsonPath = Paths.get("src/main/java/com/project/dades/url.json");
            
            if (!Files.exists(jsonPath)) {
                System.out.println("[client] Archivo url.json no encontrado");
                return null;
            }

            String content = new String(Files.readAllBytes(jsonPath));
            JSONObject jsonObject = new JSONObject(content);
            String url = jsonObject.optString("url", "");
            System.out.println("[client] URL cargada: " + url);
            return url;
            
        } catch (Exception e) {
            System.out.println("[client] Error cargando url.json: " + e.getMessage());
            return null;
        }
    }

    public void run() {
        PioMatter pm = null;
        PioMatter.FB fb = null;
        BufferedImage back = null;
        Graphics2D g = null;

        final UtilsFPS fps = new UtilsFPS();

        try {
            pm = new PioMatter(WIDTH, HEIGHT, ADDR, LANES, BRIGHTNESS, 0);
            fb = pm.mapFramebuffer();

            back = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
            g = back.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_OFF);
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);

            final Font font = new Font("SansSerif", Font.PLAIN, 12);

            // Neteja inicial
            PioMatter.flushBlack(pm, fb, 2, 10);

            while (true) {
                fps.beginFrame();

                // Si el joc està actiu, dibuixem els objectes del joc
                if (jocActiu) {

                    // Fons blau a la zona de joc
                    g.setColor(Color.BLUE);
                    g.fillRect(0, RESERVED_TOP, WIDTH, HEIGHT - RESERVED_TOP);

                    // Zona reservada pels punts, fons negre
                    g.setColor(Color.BLACK);
                    g.fillRect(0, 0, WIDTH, RESERVED_TOP);

                    g.setColor(Color.WHITE);
                    Font scoreFont = new Font("SansSerif", Font.BOLD, 10);
                    g.setFont(scoreFont);
                    FontMetrics fmTop = g.getFontMetrics();

                    // Puntuacions jugadors
                    g.drawString(String.valueOf(j1Punts), 2, fmTop.getAscent());
                    g.drawString(String.valueOf(j2Punts),
                                WIDTH - fmTop.stringWidth(String.valueOf(j2Punts)) - 2,
                                fmTop.getAscent());

                    // Dibuixem els objectes del joc
                    for (GameObject go : new ArrayList<>(gameObjects)) {
                        Color col = switch (go.color.toUpperCase()) {
                            case "RED" -> Color.RED;
                            case "BLACK" -> Color.GREEN;
                            case "WHITE" -> Color.WHITE;
                            default -> Color.GRAY;
                        };
                        g.setColor(col);
                        g.fillRect(go.x, go.y, go.ancho, go.alto);
                    }

                } else {

                    // Quan el joc no està actiu

                    // Fons negre
                    g.setColor(Color.BLACK);
                    g.fillRect(0, 0, WIDTH, HEIGHT);

                    // Dibuixar el títol a la zona reservada
                    g.setColor(Color.WHITE);
                    Font titleFont = new Font("SansSerif", Font.BOLD, 9);
                    g.setFont(titleFont);
                    FontMetrics fmTop = g.getFontMetrics();

                    g.drawString("PONG GAME", 1, fmTop.getAscent());

                    int startY = RESERVED_TOP + TEXT_TOP_PAD;
                    int availH = HEIGHT - startY;
                    int availW = WIDTH - TEXT_X;

                    boolean alive = System.currentTimeMillis() < expireAtMs;

                    if (alive && mode == Mode.TEXT && text != null) {
                        g.setFont(font);
                        g.setColor(Color.WHITE);
                        FontMetrics fm = g.getFontMetrics();

                        int textWidth = fm.stringWidth(text);

                        if (textWidth > availW && scrollingText != null) {
                            long currentTime = System.currentTimeMillis();
                            if (currentTime - lastScrollTime > 100) {
                                scrollX -= 1;
                                lastScrollTime = currentTime;
                                if (scrollX + textWidth < 0) scrollX = WIDTH;
                            }

                            g.drawString(scrollingText, TEXT_X + scrollX, startY + fm.getAscent());

                        } else {
                            List<String> lines = wrapText(text, fm, availW, availH);
                            int y = startY + fm.getAscent();
                            for (String line : lines) {
                                g.drawString(line, TEXT_X, y);
                                y += fm.getHeight();
                            }
                        }

                    } else if (alive && mode == Mode.IMAGE && image != null) {
                        UtilsImage.drawImageFit(g, image, 0, 0, WIDTH, HEIGHT, FitMode.CONTAIN);
                    }
                }


                // Zona de dibuix de text (evitant l'overlay d'FPS)
                int startY = Math.max(0, RESERVED_TOP + TEXT_TOP_PAD);
                int availH = Math.max(0, HEIGHT - startY);
                int availW = Math.max(0, WIDTH - TEXT_X);

                // Pinta segons mode si no ha caducat
                boolean alive = System.currentTimeMillis() < expireAtMs;
                if (alive || jocActiu) {
                    if (mode == Mode.TEXT && text != null) {
                        g.setFont(font);
                        g.setColor(Color.WHITE);
                        FontMetrics fm = g.getFontMetrics();

                        // Calcular ancho total del texto
                        int textWidth = fm.stringWidth(text);
                        
                        // Si el texto es más ancho que el espacio disponible y tenemos scrollingText, activar scroll
                        if (textWidth > availW && scrollingText != null) {
                            // Actualizar scroll cada 100ms
                            long currentTime = System.currentTimeMillis();
                            if (currentTime - lastScrollTime > 100) {
                                scrollX -= 1; // Mover hacia la izquierda
                                lastScrollTime = currentTime;
                                
                                // Resetear scroll cuando el texto haya salido completamente
                                if (scrollX + textWidth < 0) {
                                    scrollX = WIDTH;
                                }
                            }
                            
                            // Dibujar texto con posición de scroll
                            g.drawString(scrollingText, TEXT_X + scrollX, startY + fm.getAscent());
                            
                        } else {
                            // Texto normal sin scroll (word-wrap)
                            List<String> lines = wrapText(text, fm, availW, availH);
                            int y = startY + fm.getAscent();
                            for (String line : lines) {
                                g.drawString(line, TEXT_X, y);
                                y += fm.getHeight();
                            }
                        }

                    } else if (mode == Mode.IMAGE && image != null) {
                        // Mostra la imatge amb CONTAIN dins tota la pantalla
                        UtilsImage.drawImageFit(g, image, 0, 0, WIDTH, HEIGHT, FitMode.CONTAIN);
                    }
                } else {
                    // caducat
                    mode = Mode.NONE;
                    text = null;
                    image = null;
                    scrollingText = null; // Resetear también el texto de scroll
                    scrollX = 0;
                }

                // FPS overlay (queda per sobre)
                //fps.drawOverlay(g, 1, 9);

                // Volcat framebuffer
                PioMatter.copyBufferedImageToRGB888(back, fb.data, fb.strideBytes, WIDTH, HEIGHT, BRIGHTNESS);
                pm.swap();

                // Cap FPS
                fps.endFrameAndCap(FPS_CAP);
            }

        } catch (Throwable t) {
            t.printStackTrace();
        } finally {
            if (g != null) g.dispose();
            try { if (pm != null && fb != null) PioMatter.flushBlack(pm, fb, 2, 10); } catch (InterruptedException ignored) {}
            if (pm != null) pm.close();
            ws.forceExit();
        }
    }
    /**
     * Fa word-wrap amb mètriques (FontMetrics) respectant amplada i alçada disponibles.
     * Trunca l'última línia amb ‘…’ si no hi cap tot el text.
     */
    private static List<String> wrapText(String s, FontMetrics fm, int maxW, int maxH) {
        ArrayList<String> out = new ArrayList<>();
        if (s == null || s.isEmpty() || maxW <= 0 || maxH <= 0) return out;

        int lineH = fm.getHeight();
        int maxLines = Math.max(1, maxH / lineH);

        // Split per espais (preserva paraules); també tracta salts de línia explícits
        String[] paragraphs = s.split("\\R"); // \R = qualsevol salt de línia
        for (String para : paragraphs) {
            // Si el paràgraf és buit, afegim línia en blanc si queda espai
            if (para.isEmpty()) {
                if (out.size() < maxLines) out.add("");
                else break;
                continue;
            }

            String[] words = para.split("\\s+");
            StringBuilder line = new StringBuilder();

            for (int i = 0; i < words.length; i++) {
                String w = words[i];
                String candidate = line.length() == 0 ? w : (line + " " + w);
                if (fm.stringWidth(candidate) <= maxW) {
                    line.setLength(0);
                    line.append(candidate);
                } else {
                    // si la paraula sola ja és massa llarga, trenquem-la amb ellipsi
                    if (line.length() == 0) {
                        out.add(truncateWithEllipsis(w, fm, maxW));
                    } else {
                        out.add(line.toString());
                        // revalorem w a la següent línia
                        i--; // tornem a intentar afegir ‘w’ en una línia nova
                    }
                    line.setLength(0);
                    // si ja no hi ha espai vertical, sortim
                    if (out.size() >= maxLines) break;
                }
                if (out.size() >= maxLines) break;
            }

            if (out.size() >= maxLines) break;
            if (line.length() > 0) {
                out.add(line.toString());
            }

            if (out.size() >= maxLines) break;
        }

        // Si hem excedit l’alçada, trunquem l’última línia amb ‘…’
        if (out.size() > maxLines) {
            while (out.size() > maxLines) out.remove(out.size() - 1);
            String last = out.get(out.size() - 1);
            out.set(out.size() - 1, truncateWithEllipsis(last, fm, maxW));
        } else if (out.size() == maxLines) {
            // potser queda contingut pendent (no podem saber-ho fàcilment); marquem amb ‘…’ si la última ja és molt plena
            // (opcional; si no ho vols, comenta aquesta part)
            // out.set(out.size() - 1, truncateWithEllipsis(out.get(out.size() - 1), fm, maxW));
        }

        return out;
    }

    /** Trunca una cadena a maxW i hi afegeix ‘…’ si cal. */
    private static String truncateWithEllipsis(String s, FontMetrics fm, int maxW) {
        if (fm.stringWidth(s) <= maxW) return s;
        String ell = "…";
        int ellW = fm.stringWidth(ell);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            int w = fm.stringWidth(sb.toString() + s.charAt(i));
            if (w + ellW > maxW) break;
            sb.append(s.charAt(i));
        }
        sb.append(ell);
        return sb.toString();
    }

    public static void main(String[] args) {
        String serverURI = (args.length > 0) ? args[0] : loadUrlFromJson();
        Main app = new Main(serverURI);

        app.run();
    }
}