package org.mpisws.concurrent.programs.nondet.array;

public class Array {

  public int[] a;
  public int x;

  Array(int SIZE) {
    this.a = new int[SIZE];
    // Initialize the array with 0s
    for (int i = 0; i < SIZE; i++) {
      this.a[i] = 0;
    }
    this.x = 0;
  }
}
