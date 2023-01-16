package de.cubbossa.pathfinder.util;

import com.mojang.brigadier.context.StringRange;
import com.mojang.brigadier.suggestion.Suggestion;
import com.mojang.brigadier.suggestion.Suggestions;
import de.cubbossa.translations.Message;
import de.cubbossa.translations.TranslationHandler;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import lombok.experimental.UtilityClass;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.command.CommandSender;

@UtilityClass
public class CommandUtils {

  public Suggestions wrapWithQuotation(String command, Suggestions suggestions, boolean addEmpty) {
    List<Suggestion> result = suggestions.getList().stream()
        .map(s -> new Suggestion(StringRange.between(
            s.getRange().getStart() + 1,
            s.getRange().getEnd() + 1
        ), s.getText(), s.getTooltip()))
        .collect(Collectors.toList());
    if (addEmpty) {
      result.add(new Suggestion(StringRange.at(0), "\"\""));
    }

    return Suggestions.create(command, result);
  }

  public Suggestions offsetSuggestions(String command, Suggestions suggestions, int offset) {
    List<Suggestion> result = suggestions.getList().stream()
        .map(s -> new Suggestion(StringRange.between(
            s.getRange().getStart() + offset,
            s.getRange().getEnd() + offset
        ), s.getText(), s.getTooltip()))
        .collect(Collectors.toList());

    return Suggestions.create(command, result);
  }

  public <T> void printList(CommandSender sender, int page, int pageSize, List<T> elements,
                            Consumer<T> print, Message header, Message footer) {

    int maxPage = (int) Math.ceil(elements.size() / (float) pageSize);
    if (maxPage == 0) {
      maxPage = 1;
    }
    page = Integer.min(page, maxPage);
    int prevPage = Integer.max(page - 1, 1);
    int nextPage = Integer.min(page + 1, maxPage);

    TagResolver resolver = TagResolver.builder()
        .resolver(Placeholder.parsed("page", page + ""))
        .resolver(Placeholder.parsed("prev-page", prevPage + ""))
        .resolver(Placeholder.parsed("next-page", nextPage + ""))
        .resolver(Placeholder.parsed("pages", maxPage + ""))
        .build();


    TranslationHandler.getInstance().sendMessage(header.format(resolver), sender);
    for (T element : CommandUtils.subListPaginated(elements, page - 1, pageSize)) {
      print.accept(element);
    }
    TranslationHandler.getInstance().sendMessage(footer.format(resolver), sender);
  }

  public <T> List<T> subList(List<T> list, int offset) {
    return list.subList(Integer.min(offset, list.size()), list.size());
  }

  public <T> List<T> subList(List<T> list, int offset, int limit) {
    return list.subList(Integer.min(offset, list.size() == 0 ? 0 : list.size() - 1),
        Integer.min(limit + offset, list.size()));
  }

  public <T> List<T> subListPaginated(List<T> list, int page, int pageSize) {
    return subList(list, page * pageSize, pageSize);
  }
}
