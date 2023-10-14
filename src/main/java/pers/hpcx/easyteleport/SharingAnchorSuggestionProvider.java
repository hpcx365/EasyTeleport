package pers.hpcx.easyteleport;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public record SharingAnchorSuggestionProvider(EasyTeleport mod) implements SuggestionProvider<ServerCommandSource> {
    
    public static SharingAnchorSuggestionProvider suggestions(EasyTeleport mod) {
        return new SharingAnchorSuggestionProvider(mod);
    }
    
    @Override
    public CompletableFuture<Suggestions> getSuggestions(CommandContext<ServerCommandSource> context, SuggestionsBuilder builder) throws CommandSyntaxException {
        ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
        List<ShareRequest> requestList = mod.shareRequests.get(player.getGameProfile().getId());
        ArrayList<TeleportAnchor> anchorList = new ArrayList<>();
        for (ShareRequest request : requestList) {
            anchorList.add(request.anchor);
        }
        anchorList.sort(TeleportAnchor.COMPARATOR);
        for (TeleportAnchor anchor : anchorList) {
            builder.suggest(anchor.name());
        }
        return builder.buildFuture();
    }
}
