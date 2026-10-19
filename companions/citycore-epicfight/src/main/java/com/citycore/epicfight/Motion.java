package com.citycore.epicfight;

/**
 * Traduit un déplacement réel en « touches » au sens de Minecraft : avancer / reculer
 * (forward, positif = en avant) et pas de côté (strafe, positif = vers la gauche), par rapport
 * à la direction où regarde le PNJ. Classe sans dépendance (testable seule).
 */
final class Motion {

    private Motion() {
    }

    /**
     * @param dx     déplacement est-ouest du tick (blocs)
     * @param dz     déplacement nord-sud du tick (blocs, z vers le sud)
     * @param yawDeg orientation de la tête, en degrés (convention Minecraft : 0 = sud, 90 = ouest)
     * @return {forward, strafe}, chacun dans [−1, 1], arrondis au dixième
     */
    static float[] input(double dx, double dz, float yawDeg) {
        double len = Math.sqrt(dx * dx + dz * dz);
        if (len < 1e-9) return new float[]{0f, 0f};
        double yaw = Math.toRadians(yawDeg);
        // Direction du regard : 0° → (0, 1) = sud ; à sa gauche : l'est (+x) quand on regarde le sud
        double fx = -Math.sin(yaw), fz = Math.cos(yaw);
        double lx = Math.cos(yaw), lz = Math.sin(yaw);
        double forward = (dx * fx + dz * fz) / len;
        double strafe  = (dx * lx + dz * lz) / len;
        return new float[]{round(forward), round(strafe)};
    }

    private static float round(double v) {
        return Math.round(Math.max(-1, Math.min(1, v)) * 10) / 10f;
    }
}
