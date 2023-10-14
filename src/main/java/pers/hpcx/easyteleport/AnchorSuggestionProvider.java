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

public record AnchorSuggestionProvider(EasyTeleport mod) implements SuggestionProvider<ServerCommandSource> {
    
    public static AnchorSuggestionProvider suggestions(EasyTeleport mod) {
        return new AnchorSuggestionProvider(mod);
    }
    
    @Override
    public CompletableFuture<Suggestions> getSuggestions(CommandContext<ServerCommandSource> context, SuggestionsBuilder builder) throws CommandSyntaxException {
        ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
        Map<String, TeleportAnchor> anchors = ((TeleportStorage) player).easyTeleport$getAnchors();
        ArrayList<TeleportAnchor> anchorList = new ArrayList<>(anchors.values());
        ArrayList<TeleportAnchor> publicAnchorList = new ArrayList<>(mod.publicAnchors.values());
        anchorList.sort(TeleportAnchor.COMPARATOR);
        publicAnchorList.sort(TeleportAnchor.COMPARATOR);
        for (TeleportAnchor anchor : anchorList) {
            builder.suggest(anchor.name());
        }
        for (TeleportAnchor anchor : publicAnchorList) {
            builder.suggest(anchor.name());
        }
        return builder.buildFuture();
    }
}
