package io.nexyo.edp.extensions;

import org.eclipse.edc.boot.system.runtime.BaseRuntime;

/**
 * Runner class for the EDC extension.
 */
public class Runner extends BaseRuntime {

    /**
     * Main method to start the EDC - EDP extension.
     *
     *
     * @param args the command line arguments
     */
    public static void main(String[] args) {
        new Runner().boot(true);
    }

}