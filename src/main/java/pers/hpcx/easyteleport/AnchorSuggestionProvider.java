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
        ServerCommandSource source = context.getSource();
        ServerPlayerEntity player = source.getPlayerOrThrow();
        Map<String, Anchor> anchors = ((AnchorStorage) player).easyTeleport$getAnchors();
        ArrayList<String> names = new ArrayList<>(anchors.keySet());
        names.sort(String::compareToIgnoreCase);
        for (String name : names) {
            builder.suggest(name);
        }
        return builder.buildFuture();
    }
}
