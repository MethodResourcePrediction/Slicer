package de.uniks.vs.methodresourceprediction.slicer.test;

public class DependencyValidation {
  public static int staticMethodWithoutDependencies(int someNumber) {
    return someNumber;
  }

  private static final int STATIC_CONSTANT = 10;

  public static int staticMethodWithStaticFieldDependency(int someNumber) {
    return someNumber + STATIC_CONSTANT;
  }

  // Public access to prevent replacing to constant optimization
  public static int staticVariable = 20;

  public static int staticMethodWithStaticVariableDependency(int someNumber) {
    return someNumber + staticVariable;
  }

  public int methodWithoutDependency(int someNumber) {
    return someNumber;
  }

  public long methodWithJavaLangDependency(int someNumber) {
    return someNumber + Thread.currentThread().getId();
  }

  public static int staticMethodWithJavaLangDependency(int someNumber) {
    return (int) (someNumber + Thread.currentThread().getId());
  }

  // Public access to prevent replacing to constant optimization
  public int variable = 20;

  public int methodWithVariableDependency(int someNumber) {
    return someNumber + variable;
  }

  public int methodCallWithoutDependency(int someNumber) {
    return methodWithoutDependency(someNumber);
  }

  public int methodCallWithDependency(int someNumber) {
    return methodWithVariableDependency(someNumber);
  }

  public static int staticMethodNew(int someNumber) {
    return new DependencyValidation().methodCallWithDependency(someNumber);
  }

  public static int staticMethodCallWithoutDependency(int someNumber) {
    return staticMethodWithoutDependencies(someNumber);
  }

  public static int staticMethodCallWithDependency(int someNumber) {
    return staticMethodWithStaticVariableDependency(someNumber);
  }
}
