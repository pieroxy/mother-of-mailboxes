package net.pieroxy.mom.services;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class ServiceOrderingTest {
  private static class ServiceA implements Service {
  }

  private static class ServiceB implements Service {
    @Override
    public List<Class<? extends Service>> getDependencies() {
      return List.of(ServiceA.class);
    }
  }

  private static class ServiceC implements Service {
    @Override
    public List<Class<? extends Service>> getDependencies() {
      return List.of(ServiceB.class);
    }
  }

  @Test
  public void ordersServicesSoEachComesAfterEveryDependencyItDeclared() {
    ServiceA a = new ServiceA();
    ServiceB b = new ServiceB();
    ServiceC c = new ServiceC();

    // Deliberately handed in out of order, to prove the sort decides the sequence, not list order.
    List<Service> ordered = ServiceOrdering.topologicalOrder(List.of(c, a, b));

    assertEquals(List.of(a, b, c), ordered);
  }

  private static class DependsOnAnUnregisteredService implements Service {
    @Override
    public List<Class<? extends Service>> getDependencies() {
      return List.of(ServiceA.class);
    }
  }

  @Test
  public void throwsWhenADeclaredDependencyIsntARegisteredService() {
    try {
      ServiceOrdering.topologicalOrder(List.of(new DependsOnAnUnregisteredService()));
      fail("expected an IllegalStateException naming the unregistered dependency");
    } catch (IllegalStateException e) {
      assertTrue(e.getMessage().contains("ServiceA"));
    }
  }

  private static class CyclicX implements Service {
    @Override
    public List<Class<? extends Service>> getDependencies() {
      return List.of(CyclicY.class);
    }
  }

  private static class CyclicY implements Service {
    @Override
    public List<Class<? extends Service>> getDependencies() {
      return List.of(CyclicX.class);
    }
  }

  @Test(expected = IllegalStateException.class)
  public void throwsOnACircularDependency() {
    ServiceOrdering.topologicalOrder(List.of(new CyclicX(), new CyclicY()));
  }
}
