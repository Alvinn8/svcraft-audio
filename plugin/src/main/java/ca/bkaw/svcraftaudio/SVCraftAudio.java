package ca.bkaw.svcraftaudio;

import org.bukkit.plugin.java.JavaPlugin;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

public final class SVCraftAudio extends JavaPlugin {
    private Config config;
    private UpdateTask updateTask;
    private Connection connection;

    @Override
    public void onEnable() {
        this.loadConfig();
    }

    @Override
    public void onDisable() {
        // Plugin shutdown logic
    }

    private void loadConfig() {
        this.config = new Config(this.getConfig());

        List<String> header = new ArrayList<>();
        InputStream inputStream = this.getResource("config-header.txt");
        if (inputStream == null) {
            throw new RuntimeException("No config-header.txt file present in the plugin jar.");
        }
        try (BufferedReader in = new BufferedReader(new InputStreamReader(inputStream))) {
            String line;
            while ((line = in.readLine()) != null) {
                header.add(line);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        this.getConfig().options().setHeader(header);

        this.getConfig().options().copyDefaults(true);
        this.saveConfig();
    }
}
