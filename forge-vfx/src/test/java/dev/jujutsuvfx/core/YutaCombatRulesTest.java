package dev.jujutsuvfx.core;
public final class YutaCombatRulesTest {
 public static void main(String[] args) throws Exception {
  Class<?> c=Class.forName("dev.jujutsuvfx.core.YutaCombatRules");
  var amp=c.getMethod("amplification",boolean.class,boolean.class,boolean.class);
  check((float)amp.invoke(null,true,true,true)==1.25f,"player spell +25%");
  check((float)amp.invoke(null,true,true,false)==1f,"ordinary melee unchanged");
  check((float)amp.invoke(null,false,true,true)==1f,"NPC separate balance");
  var guard=c.getMethod("guardMultiplier",boolean.class,boolean.class,boolean.class);
  check(Math.abs((float)guard.invoke(null,true,true,false)-.2f)<.0001,"sword blocks 80%");
  check((float)guard.invoke(null,false,true,false)==1f,"sheathed cannot guard");
  check((float)guard.invoke(null,true,false,false)==1f,"rear contact bypasses guard");
  check((float)guard.invoke(null,true,true,true)==1f,"kill bypasses guard");
  System.out.println("Yuta directional guard and player amplification passed");
 }
 static void check(boolean yes,String why){if(!yes)throw new AssertionError(why);}
}
