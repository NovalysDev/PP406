package com.cockpit406.diagnostic;

/** Verified references only, scoped to the identified ES9J4 / Bosch MP7.0. */
public final class ComparisonRules {
    public static final class Rule {
        public final Double lower,upper;public final boolean inclusiveUpper;public final String description;
        Rule(Double lower,Double upper,boolean inclusiveUpper,String description){this.lower=lower;this.upper=upper;this.inclusiveUpper=inclusiveUpper;this.description=description;}
        public boolean flagged(double value){return Double.isFinite(value)&&((lower!=null&&value<lower)||(upper!=null&&(inclusiveUpper?value>=upper:value>upper)));}
    }
    public static Rule find(String metric,String reference,String vin){
        if(!EngineValueGuide.v6Profile(reference,vin))return null;
        switch(metric){
            case "rpm":return new Rule(null,6800d,true,"Repère Peugeot : coupure d’injection au-dessus de 6 800 tr/min (ES9J4). Atteint ou dépassé : à examiner.");
            case "oxygen_mv":return new Rule(0d,1000d,false,"Repère électrique MP7.0 : 0 à 1 000 mV. Un signal bas isolé est normal ; hors plage ne signifie pas automatiquement panne.");
            case "throttle_mv":return new Rule(0d,5000d,false,"Repère électrique MP7.0 : 0 à 5 000 mV. Hors plage : lecture ou circuit à vérifier.");
            default:return null;
        }
    }
    private ComparisonRules(){}
}
