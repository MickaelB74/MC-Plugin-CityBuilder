package com.citycore.inhabitant;

import com.citycore.building.Building;
import com.citycore.npc.CityNPC;
import org.bukkit.Material;

/**
 * Lieu de travail des employés d'un artisan (emplois) : son annexe de travail, la durée d'un
 * cycle de travail et la production d'un cycle dans son entrepôt. Fourni par JobsManager.
 */
public interface WorkPlace {

    /** Annexe de travail de l'artisan, ou null s'il n'en a pas. */
    Building annex(CityNPC employer);

    /** Raison pour laquelle ses employés ne peuvent pas travailler (artisan sans atelier, annexe absente ou incomplète), ou null. */
    String blocked(CityNPC employer);

    /** Durée d'un cycle de travail (ms) selon le niveau de son annexe, ou -1 s'il ne peut pas travailler. */
    long cycleMs(CityNPC employer);

    /** Fin d'un cycle d'un employé : ce qu'il a produit dans l'entrepôt de l'artisan ; renvoie le nombre d'objets. */
    int produce(CityNPC employer);

    /** Objet tenu en main par ses employés (outil du métier). */
    Material tool(CityNPC employer);

    /** Matière des particules du travail (bloc ou objet du métier). */
    Material workMaterial(CityNPC employer);
}
