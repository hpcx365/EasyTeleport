package pers.hpcx.easyteleport;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.minecraft.server.command.ServerCommandSource;

import java.util.ArrayList;
import java.util.concurrent.CompletableFuture;

public record PublicAnchorSuggestionProvider(EasyTeleport mod) implements SuggestionProvider<ServerCommandSource> {
    
    public static PublicAnchorSuggestionProvider suggestions(EasyTeleport mod) {
        return new PublicAnchorSuggestionProvider(mod);
    }
    
    @Override
    public CompletableFuture<Suggestions> getSuggestions(CommandContext<ServerCommandSource> context, SuggestionsBuilder builder) {
        ArrayList<String> publicAnchorNames = new ArrayList<>(mod.publicAnchors.keySet());
        publicAnchorNames.sort(String::compareToIgnoreCase);
        for (String anchorName : publicAnchorNames) {
            builder.suggest(anchorName);
        }
        return builder.buildFuture();
    }
}
