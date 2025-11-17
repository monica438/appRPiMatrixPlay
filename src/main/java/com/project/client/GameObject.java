package com.project.client;

import org.json.JSONObject;

public class GameObject {
    public String id;
    public int x;
    public int y;
    public int ancho;
    public int alto;
    public String color; 
    public GameObject(String id, int x, int y, int ancho, int alto,String color) {
        this.id = id;
        this.x = x;
        this.y = y;
        this.ancho = ancho;
        this.alto = alto;
        this.color = color;
    }

    @Override
    public String toString() {
        return this.toJSON().toString();
    }
    
    // Converteix l'objecte a JSON
    public JSONObject toJSON() {
        JSONObject obj = new JSONObject();
        obj.put("id", id);
        obj.put("x", x);
        obj.put("y", y);
        obj.put("ancho", ancho);
        obj.put("alto", alto);
        obj.put("color", color);
        return obj;
    }

    // Crea un GameObjects a partir de JSON
    public static GameObject fromJSON(JSONObject obj, int ampladaFinestra, int alcadaFinestra) {

        int xLog = obj.optInt("x", 0);
        int yLog = obj.optInt("y", 0);
        int ampleLog = obj.optInt("ancho", 1);
        int altLog = obj.optInt("alto", 1);

        int xPix = (int) ((xLog / 600f) * ampladaFinestra);
        int yPix = (int) ((yLog / 400f) * alcadaFinestra);
        int amplePix = (int) ((ampleLog / 600f) * ampladaFinestra);
        int altPix = (int) ((altLog / 400f) * alcadaFinestra);

        return new GameObject(
            obj.optString("id", null),
            xPix,
            yPix,
            amplePix,
            altPix,
            obj.optString("color", "gray")
        );
    }

    public static GameObject fromJSONScaledToGameArea(
            JSONObject obj,
            int pantallaAncho,
            int pantallaAlto,
            int reservedTop,
            int logicWidth,
            int logicHeight) {

        // Datos lógicos (del servidor)
        int xLog = obj.optInt("x", 0);
        int yLog = obj.optInt("y", 0);
        int ampleLog = obj.optInt("ancho", 1);
        int altLog = obj.optInt("alto", 1);

        // Área jugable real (zona azul)
        int gameHeight = pantallaAlto - reservedTop;

        // Cálculo de escalado
        float scaleX = pantallaAncho / (float) logicWidth;
        float scaleY = gameHeight / (float) logicHeight;

        // Convertir coordenadas a pixel
        int xPix = (int) (xLog * scaleX);
        int amplePix = (int) (ampleLog * scaleX);

        // ESCALAR Y dentro del área azul
        int yPix = reservedTop + (int) (yLog * scaleY);
        int altPix = (int) (altLog * scaleY);

        return new GameObject(
            obj.optString("id", ""),
            xPix,
            yPix,
            amplePix,
            altPix,
            obj.optString("color", "gray")
        );
    }


}
