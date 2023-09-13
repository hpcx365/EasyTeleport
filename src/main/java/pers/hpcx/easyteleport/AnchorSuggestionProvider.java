package pers.hpcx.easyteleport;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public class AnchorSuggestionProvider implements SuggestionProvider<ServerCommandSource> {
    
    public static AnchorSuggestionProvider suggestions() {
        return new AnchorSuggestionProvider();
    }
    
    @Override
    public CompletableFuture<Suggestions> getSuggestions(CommandContext<ServerCommandSource> context, SuggestionsBuilder builder)
            throws CommandSyntaxException {
        ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
        Map<String, TeleportAnchor> anchors = ((TeleportStorage) player).easyTeleport$getAnchors();
        if (!anchors.isEmpty()) {
            ArrayList<String> anchorNames = new ArrayList<>(anchors.keySet());
            anchorNames.sort(String::compareToIgnoreCase);
            for (String anchorName : anchorNames) {
                builder.suggest(anchorName);
            }
        }
        return builder.buildFuture();
    }
}
