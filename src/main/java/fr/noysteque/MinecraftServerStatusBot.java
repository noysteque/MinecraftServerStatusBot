package fr.noysteque;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.TextChannel;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.json.JSONObject;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class MinecraftServerStatusBot extends ListenerAdapter {
    private final String panelURL;
    private final String apiKey;
    private final String serverID;
    private final String channelId;
    private final OkHttpClient httpClient;
    private final Map<String, Long> cooldowns;
    private static JDA jda;

    public MinecraftServerStatusBot(String panelURL, String apiKey, String serverID, String channelId) {
        this.panelURL = panelURL;
        this.apiKey = apiKey;
        this.serverID = serverID;
        this.channelId = channelId;
        this.httpClient = new OkHttpClient();
        this.cooldowns = new HashMap<>();
    }

    public static void main(String[] args) throws Exception {
        String discordToken = "YOUR_DISCORD_BOT_TOKEN";
        String panelURL = "YOUR_PTERODACTYL_PANEL_URL"; // You have to remove the / at the end of your pterodactyl URL (ex : https://your-panel-link.com)
        String apiKey = "YOUR_PTERODACTYL_API_KEY"; // You must use the Client API key, and not Application. You can create here : https://your-panel-link.com/account/api
        String serverID = "YOUR_PTERODACTYL_SERVER_ID"; // Pterodactyl server ID, is located in the link (ex :https://your-panel-link.com/server/a4492898
        String channelId = "YOUR_DISCORD_CHANNEL_ID"; // The discord channel, where the bot will send the status message

        jda = JDABuilder.createDefault(discordToken)
                .addEventListeners(new MinecraftServerStatusBot(panelURL, apiKey, serverID, channelId))
                .setActivity(Activity.watching("MyMinecraftServer")) //You can replace MyMinecraftServer, by your server name
                .build();
    }

    @Override
    public void onMessageReceived(@Nonnull MessageReceivedEvent event) {
        Message message = event.getMessage();
        String content = message.getContentRaw();

        if (content.equalsIgnoreCase("!serverstatus")) {
            if (isOnCooldown(event.getAuthor().getId())) {
                event.getChannel().sendMessage("Command is on cooldown. Please wait before using it again.").queue();
            } else {
                try {
                    String status = getServerStatus(event.getTextChannel());
                    event.getChannel().sendMessage(status).queue();
                    setCooldown(event.getAuthor().getId());
                } catch (IOException e) {
                    event.getChannel().sendMessage("Failed to get server status.").queue();
                    e.printStackTrace();
                }
            }
        }
    }

    private boolean isOnCooldown(String userId) {
        if (!cooldowns.containsKey(userId)) {
            return false;
        }
        long currentTime = System.currentTimeMillis();
        long cooldownTime = cooldowns.get(userId);
        return currentTime - cooldownTime < 5000; // 5 seconds cooldown
    }

    private void setCooldown(String userId) {
        long currentTime = System.currentTimeMillis();
        cooldowns.put(userId, currentTime);
    }

    private String getServerStatus(TextChannel channel) throws IOException {
        Request request = new Request.Builder()
                .url(panelURL + "/api/client/servers/" + serverID + "/resources")
                .addHeader("Accept", "application/json")
                .addHeader("Content-Type", "application/json")
                .addHeader("Authorization", "Bearer " + apiKey)
                .get()
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            int responseCode = response.code();
            String responseBody = response.body().string();
            System.out.println("Response code: " + responseCode);
            System.out.println("Response body: " + responseBody);
            if (response.isSuccessful()) {
                JSONObject serverData = new JSONObject(responseBody);
                JSONObject attributes = serverData.getJSONObject("attributes");
                boolean isInstalling = attributes.has("is_installing") && attributes.getBoolean("is_installing");

                String currentState = attributes.optString("current_state");

                if (isInstalling) {
                    jda.getPresence().setActivity(Activity.watching("MyMinecraftServer"));
                    return "The server is currently installing.";
                } else if ("running".equalsIgnoreCase(currentState)) {
                    int playerCount = getPlayerCount(); // Get the player count from the Minecraft server
                    jda.getPresence().setActivity(Activity.watching("Players: " + playerCount + "/20"));
                    return "The server is online. Current state: " + currentState + ". Players: " + playerCount + "/20";
                } else {
                    jda.getPresence().setActivity(Activity.watching("MyMinecraftServer"));
                    return "The server is offline or in an unknown state.";
                }
            } else {
                return "Failed to get server status. Response code: " + response.code();
            }
        }
    }


    private int getPlayerCount() throws IOException {
        Request request = new Request.Builder()
                .url("https://mcapi.us/server/status?ip=your-server-ip:port") // replace your-server-ip:port with your servers IP include the port.
                .get()
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            int responseCode = response.code();
            String responseBody = response.body().string();
            System.out.println("Response code: " + responseCode);
            System.out.println("Response body: " + responseBody);
            if (response.isSuccessful()) {
                JSONObject serverStatus = new JSONObject(responseBody);
                if (serverStatus.getString("status").equalsIgnoreCase("success")) {
                    JSONObject playersData = serverStatus.getJSONObject("players");
                    return playersData.getInt("now");
                }
            }
        }
        return 0;
    }
}
