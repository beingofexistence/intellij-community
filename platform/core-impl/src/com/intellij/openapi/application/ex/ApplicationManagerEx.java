// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.application.ex;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.util.indexing.impl.IndexDebugProperties;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.util.concurrent.CountDownLatch;

public final class ApplicationManagerEx extends ApplicationManager {
  public static final String IS_INTERNAL_PROPERTY = "idea.is.internal";

  private static volatile boolean inStressTest;
  private static volatile CountDownLatch isInitialStart;

  public static ApplicationEx getApplicationEx() {
    return (ApplicationEx)ourApplication;
  }

  public static boolean isInStressTest() {
    return inStressTest;
  }

  public static boolean isInIntegrationTest() {
    return Boolean.getBoolean("idea.is.integration.test");
  }

  @TestOnly
  public static void setInStressTest(boolean value) {
    inStressTest = value;
    IndexDebugProperties.IS_IN_STRESS_TESTS = value;
  }

  public static void setInitialStart() {
    isInitialStart = new CountDownLatch(1);
  }

  public static boolean isInitialStart() {
    return getInitialStartState() != null;
  }

  @Nullable
  public static CountDownLatch getInitialStartState() {
    return isInitialStart;
  }


}
