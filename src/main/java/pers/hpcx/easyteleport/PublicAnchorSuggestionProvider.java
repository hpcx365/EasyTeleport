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
        ArrayList<TeleportAnchor> publicAnchorList = new ArrayList<>(mod.publicAnchors.values());
        publicAnchorList.sort(TeleportAnchor.COMPARATOR);
        for (TeleportAnchor anchor : publicAnchorList) {
            builder.suggest(anchor.name());
        }
        return builder.buildFuture();
    }
}
