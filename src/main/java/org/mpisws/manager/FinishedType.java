package org.mpisws.manager;

import java.io.Serializable;

/**
 * The FinishedType enum is used to indicate the type of task that has finished. The enum contains
 * three values: SUCCESS, BUG, and DEADLOCK.
 */
public enum FinishedType implements Serializable {
  SUCCESS,
  BUG,
  DEADLOCK
}
