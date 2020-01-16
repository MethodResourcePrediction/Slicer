package de.rherzog.master.thesis.slicer.test;

import org.junit.Test;

public class Signaling {

	@Test
	public void test() throws InterruptedException {
		Runtime.getRuntime().addShutdownHook(new Thread() {
			@Override
			public void run() {
				System.out.println("Shutdown hook ran!");
			}
		});

		int i = 3;
		while (i > 0) {
			Thread.sleep(1000);
//			i--;
		}
	}

}
