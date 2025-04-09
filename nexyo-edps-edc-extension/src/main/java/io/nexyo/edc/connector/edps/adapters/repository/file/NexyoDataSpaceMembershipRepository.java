package io.nexyo.edc.connector.edps.adapters.repository.file;

import org.eclipse.edc.spi.EdcException;

import java.io.*;
import java.util.ArrayList;
import java.util.List;

public class NexyoDataSpaceMembershipRepository {

    private final String filePath;

    public NexyoDataSpaceMembershipRepository(String filePath) throws EdcException {
        this.filePath = filePath;
        try {
            ensureFileExists();
        } catch (IOException e) {
            throw new EdcException("Failed to create file to store nexyo data space memberships: " + filePath, e);
        }
    }

    public List<String> getAllMemberships() throws IOException {
        List<String> ids = new ArrayList<>();
        try (BufferedReader br = new BufferedReader(new FileReader(filePath))) {
            String line;
            while ((line = br.readLine()) != null) {
                ids.add(line.trim());
            }
        }
        return ids;
    }

    public void addMembership(String did) throws IOException {
        try (BufferedWriter bw = new BufferedWriter(new FileWriter(filePath, true))) {
            bw.write(did);
            bw.newLine();
        }
    }

    public boolean containsDID(String did) throws IOException {
        List<String> ids = getAllMemberships();
        return ids.contains(did);
    }

    public void deleteDID(String did) throws IOException {
        List<String> memberships = getAllMemberships();
        if (!memberships.remove(did)) {
            return; // ID not found
        }

        // Rewrite the file without the deleted ID
        try (BufferedWriter bw = new BufferedWriter(new FileWriter(filePath))) {
            for (String currDID : memberships) {
                bw.write(currDID);
                bw.newLine();
            }
        }
    }

    public void clear() throws IOException {
        try (BufferedWriter bw = new BufferedWriter(new FileWriter(filePath))) {
            bw.write("");
        }
    }

    private void ensureFileExists() throws IOException {
        File file = new File(filePath);
        if (!file.exists()) {
            file.createNewFile();
        }
    }
}
