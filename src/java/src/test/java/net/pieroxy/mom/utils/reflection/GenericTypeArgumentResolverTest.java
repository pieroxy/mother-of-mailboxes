package net.pieroxy.mom.utils.reflection;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class GenericTypeArgumentResolverTest {
  private static class Base<A, B> {
  }

  private static class Direct extends Base<String, Integer> {
  }

  private static class Middle<A, B> extends Base<A, B> {
  }

  private static class Indirect extends Middle<String, Integer> {
  }

  @Test
  public void resolvesBothArgumentsOnADirectSubclass() {
    assertEquals(String.class, GenericTypeArgumentResolver.resolve(Direct.class, Base.class, 0));
    assertEquals(Integer.class, GenericTypeArgumentResolver.resolve(Direct.class, Base.class, 1));
  }

  @Test
  public void resolvesBothArgumentsThroughAnIntermediateGenericSuperclass() {
    assertEquals(String.class, GenericTypeArgumentResolver.resolve(Indirect.class, Base.class, 0));
    assertEquals(Integer.class, GenericTypeArgumentResolver.resolve(Indirect.class, Base.class, 1));
  }

  @Test(expected = IllegalArgumentException.class)
  public void rejectsATargetThatIsNotAnAncestor() {
    GenericTypeArgumentResolver.resolve(Direct.class, String.class, 0);
  }
}
