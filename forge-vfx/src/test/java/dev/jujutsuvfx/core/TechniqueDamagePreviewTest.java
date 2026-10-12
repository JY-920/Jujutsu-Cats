package dev.jujutsuvfx.core;

public final class TechniqueDamagePreviewTest {
    private static void range(TechniqueDamage technique,boolean cat,boolean player,boolean cinematic,int percent,
                              float low,float high) {
        var actual=TechniqueDamagePreview.sample(technique,cat,player,cinematic,percent);
        if(Math.abs(actual.min()-low)>.0001f||Math.abs(actual.max()-high)>.0001f)
            throw new AssertionError(technique+" wrong preview: "+actual+" expected "+low+".."+high);
    }
    public static void main(String[] args) {
        range(TechniqueDamage.BLUE,false,false,false,100,6,6);
        range(TechniqueDamage.BLUE,true,false,false,200,14.4f,14.4f);
        range(TechniqueDamage.BLUE,false,true,false,100,.48f,.48f);
        range(TechniqueDamage.BLUE,true,true,false,100,4.8f,4.8f);
        range(TechniqueDamage.RED,false,false,false,200,80,320);
        range(TechniqueDamage.RED,true,false,false,100,21.6f,84);
        range(TechniqueDamage.RED,true,true,false,200,12.6f,40);
        range(TechniqueDamage.PURPLE_LARGE,true,false,false,150,360,360);
        range(TechniqueDamage.PURPLE_AMPLIFIED,false,true,false,200,12,12);
        range(TechniqueDamage.PURPLE_AMPLIFIED,true,true,false,100,28,28);
        range(TechniqueDamage.PURPLE_UNLIMITED,false,false,false,100,240,240);
        range(TechniqueDamage.PURPLE_UNLIMITED,true,true,false,100,38,38);
        range(TechniqueDamage.PURPLE_UNLIMITED,false,true,true,200,1998,1998);
        range(TechniqueDamage.DISMANTLE,true,false,false,100,59,59);
        range(TechniqueDamage.CLEAVE,true,true,false,100,14,14);
        range(TechniqueDamage.FURNACE,false,false,false,50,30,100);
        range(TechniqueDamage.FURNACE,false,true,false,200,9.6f,12);
        range(TechniqueDamage.SHRINE,false,false,false,100,20,50);
        range(TechniqueDamage.SHRINE,true,true,false,100,4,6);
        for(var technique:TechniqueDamage.values())range(technique,true,true,true,0,0,0);
        if(!"0.0048".equals(TechniqueDamagePreview.sample(TechniqueDamage.BLUE,false,true,false,1).text()))
            throw new AssertionError("Small nonzero damage must not be displayed as zero");
        if(!"400".equals(TechniqueDamagePreview.sample(TechniqueDamage.PURPLE_SMALL,false,false,false,200).text()))
            throw new AssertionError("Whole damage should have no floating point noise");
        System.out.println("Damage preview: caster/target balance, ranges, cinematic damage, zero and small values passed.");
    }
}
