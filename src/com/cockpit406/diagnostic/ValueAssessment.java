package com.cockpit406.diagnostic;

/** Combines display rules without allowing a less severe rule to hide an alarm.
 * Pure presentation: no sampling, vehicle requests or stored measurements. */
public final class ValueAssessment {
    public static final int GREEN=0xff50dc90, YELLOW=0xffffe45c, ORANGE=0xffff9b40, RED=0xffff5858;
    public final ValueAlerts.Level level;
    public final String label, explanation;
    private final long flash;
    private ValueAssessment(ValueAlerts.Level level,String label,String explanation){this(level,label,explanation,level==ValueAlerts.Level.RED?350:0);}
    private ValueAssessment(ValueAlerts.Level level,String label,String explanation,long flash){this.level=level;this.label=label;this.explanation=explanation;this.flash=flash;}
    public int color(int neutral){return level==ValueAlerts.Level.RED?RED:level==ValueAlerts.Level.AMBER?ORANGE:level==ValueAlerts.Level.YELLOW?YELLOW:level==ValueAlerts.Level.NORMAL?GREEN:neutral;}
    public float alpha(long now){return flash>0&&(now/flash)%2==1?.3f:1f;}
    public static ValueAssessment tiers(String key,Double value,boolean fresh,double[] limits,boolean enabled){
        if(!enabled||!fresh||value==null)return new ValueAssessment(ValueAlerts.Level.NEUTRAL,"Non évaluée","Pas d’alerte d’affichage active sur cette mesure.",0);
        if(value.isNaN()||value.isInfinite()||value<0)return new ValueAssessment(ValueAlerts.Level.RED,"Mesure incohérente","Valeur reçue invalide pour ce compteur.",0);
        ValueAlerts.Level level=value>=limits[2]?ValueAlerts.Level.RED:value>=limits[1]?ValueAlerts.Level.AMBER:value>=limits[0]?ValueAlerts.Level.YELLOW:ValueAlerts.Level.NORMAL;
        return new ValueAssessment(level,level==ValueAlerts.Level.NORMAL?"Sous les seuils d’affichage":"Seuil d’affichage atteint",
            "Seuils personnels PP406 : jaune dès "+limits[0]+", orange dès "+limits[1]+", rouge dès "+limits[2]+". "+(key.equals("rpm")?"Aiguille rouge dès 6000 tr/min ; zone rouge du cadran de 6400 à 8000 tr/min ; clignotement rapide dès 6400 tr/min. ":"")+"Ces couleurs ne constituent pas une plage constructeur et ne modifient pas le calculateur.",key.equals("rpm")&&level==ValueAlerts.Level.RED&&value>=ValueAlerts.RPM_FAST_FLASH?80:0);
    }
    public static ValueAssessment combine(Double value,boolean fresh,Double personalHigh,EngineValueGuide.Result... guides){
        if(!fresh||value==null)return new ValueAssessment(ValueAlerts.Level.NEUTRAL,"Non évaluée","Aucune mesure récente : les couleurs sont suspendues.");
        if(value.isNaN()||value.isInfinite())return new ValueAssessment(ValueAlerts.Level.RED,"Mesure incohérente","La valeur numérique reçue ne peut pas être interprétée.");
        ValueAlerts.Level personal=ValueAlerts.evaluate(value,true,personalHigh);
        ValueAssessment result=new ValueAssessment(personal,
            personal==ValueAlerts.Level.RED?"Seuil personnel dépassé":personal==ValueAlerts.Level.AMBER?"Proche du seuil personnel":personal==ValueAlerts.Level.NORMAL?"Sous le seuil personnel":"Référence à vérifier",
            personal==ValueAlerts.Level.NEUTRAL?"Pas de plage constructeur validée ni de seuil personnel pour cette mesure.":"Seuil haut personnel PP406 : "+personalHigh+". Orange à 90 % ; rouge au seuil. Cette règle ne valide pas à elle seule le fonctionnement de la sonde.");
        for(EngineValueGuide.Result guide:guides){
            if(guide==null)continue;
            ValueAlerts.Level level=guide.level==EngineValueGuide.Level.RED?ValueAlerts.Level.RED:guide.level==EngineValueGuide.Level.ORANGE?ValueAlerts.Level.AMBER:guide.level==EngineValueGuide.Level.NORMAL?ValueAlerts.Level.NORMAL:ValueAlerts.Level.NEUTRAL;
            // At equal severity prefer the documented comparison over a personal threshold.
            if(rank(level)>0&&rank(level)>=rank(result.level))result=new ValueAssessment(level,guide.label,guide.explanation);
        }
        return result;
    }
    /** Personal display range, including zero and negative temperatures. No factory claim. */
    public static ValueAssessment range(Double value,boolean fresh,Double low,Double high){
        if(!fresh||value==null)return new ValueAssessment(ValueAlerts.Level.NEUTRAL,"Non évaluée","Aucune mesure récente : couleurs suspendues.",0);
        if(!ValueAlerts.finite(value))return new ValueAssessment(ValueAlerts.Level.RED,"Mesure incohérente","Valeur numérique non interprétable.",0);
        if((low==null&&high==null)||!ValueAlerts.validRange(low,high))return new ValueAssessment(ValueAlerts.Level.NEUTRAL,"Plage non renseignée","Aucune plage constructeur vérifiée ni plage personnelle renseignée : cette mesure n’est pas déclarée bonne.",0);
        boolean below=low!=null&&value<low,above=high!=null&&value>high;
        String bounds=(low==null?"sans minimum":low.toString())+" à "+(high==null?"sans maximum":high.toString());
        return new ValueAssessment(below||above?ValueAlerts.Level.RED:ValueAlerts.Level.NORMAL,
            below?"Sous le minimum personnel":above?"Au-dessus du maximum personnel":"Dans la plage personnelle",
            "Plage personnelle PP406 : "+bounds+". Rouge strictement hors plage ; vert dans les limites renseignées. Ce réglage d’affichage ne constitue pas une tolérance constructeur et ne valide pas la sonde.",0);
    }
    public static ValueAssessment guide(Double value,boolean fresh,EngineValueGuide.Result guide){return combine(value,fresh,null,guide);}
    private static int rank(ValueAlerts.Level level){return level==ValueAlerts.Level.RED?4:level==ValueAlerts.Level.AMBER?3:level==ValueAlerts.Level.YELLOW?2:level==ValueAlerts.Level.NORMAL?1:0;}
}
