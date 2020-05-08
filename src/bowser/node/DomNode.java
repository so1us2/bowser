package bowser.node;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;

import com.google.common.base.CharMatcher;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;

import ox.Log;

public class DomNode {

  private static final CharMatcher IS_NEWLINE = CharMatcher.is('\n');

  public DomNode parent;

  public final String tag;
  protected final List<String> attributes = new ArrayList<>(0);
  protected final List<DomNode> children = Lists.newArrayList();

  public boolean generateWhitespace = false;

  public DomNode() {
    this("");
  }

  static int c = 0;

  public DomNode(String tag) {
    this.tag = tag;
  }

  public DomNode(DomNode n) {
    this.tag = n.tag;
    this.attributes.addAll(n.attributes);
    this.children.addAll(n.children);
  }

  public DomNode replace(DomNode child, DomNode replacement) {
    return replace(child, ImmutableList.of(replacement));
  }

  public DomNode replace(DomNode child, List<DomNode> replacements) {
    checkState(children.contains(child));
    for (DomNode replacement : replacements) {
      checkState(!children.contains(replacement));
    }
    int index = children.indexOf(child);
    children.remove(index);
    children.addAll(index, replacements);

    for (DomNode node : replacements) {
      node.parent = this;
    }

    return this;
  }

  public DomNode add(DomNode... children) {
    return add(Arrays.asList(children));
  }

  public DomNode add(DomNode child) {
    checkNotNull(child);
    this.children.add(child);
    child.parent = this;
    return this;
  }

  public DomNode add(Iterable<DomNode> children) {
    children.forEach(this::add);
    return this;
  }

  public DomNode remove(DomNode child) {
    children.remove(child);
    return this;
  }

  public List<DomNode> getChildren() {
    return children;
  }

  public String get(int index) {
    DomNode node = children.get(index);
    List<DomNode> buffer = Lists.newArrayList();
    node.find(n -> n instanceof TextNode, buffer);
    TextNode t = (TextNode) buffer.get(0);
    return t.content;
  }

  public List<DomNode> getAllNodes() {
    List<DomNode> ret = Lists.newArrayList();
    find(node -> true, ret);
    return ret;
  }

  public List<DomNode> find(String tag) {
    List<DomNode> ret = Lists.newArrayList();
    find(node -> tag.equals(node.tag), ret);
    return ret;
  }

  private void find(Predicate<DomNode> filter, List<DomNode> buffer) {
    if (filter.test(this)) {
      buffer.add(this);
    }

    for (DomNode child : children) {
      child.find(filter, buffer);
    }
  }

  public List<String> getAttributes() {
    return attributes;
  }

  public boolean hasAttribute(String key) {
    return attributeIndex(key) != -1;
  }

  public String getAttribute(String key) {
    return getAttribute(key, null);
  }

  public String getAttribute(String key, String defaultValue) {
    int i = attributeIndex(key);
    return i == -1 ? defaultValue : attributes.get(i + 1);
  }

  private int attributeIndex(String key) {
    for (int i = 0; i < attributes.size(); i += 2) {
      if (attributes.get(i).equals(key)) {
        return i;
      }
    }
    return -1;
  }

  public DomNode removeAttribute(String key) {
    int index = attributeIndex(key);
    attributes.remove(index);
    attributes.remove(index);
    return this;
  }

  public List<String> getClasses() {
    String s = getAttribute("class");
    if (s == null) {
      return ImmutableList.of();
    }
    return Splitter.on(' ').omitEmptyStrings().trimResults().splitToList(s);
  }

  public DomNode text(String text) {
    children.clear();
    add(new TextNode(text));
    return this;
  }

  public String text() {
    StringBuilder sb = new StringBuilder();
    children.forEach(child -> {
      sb.append(child.text());
    });
    return sb.toString();
  }

  public DomNode id(String id) {
    return attribute("id", id);
  }

  /**
   * Sets the tooltip.
   */
  public DomNode title(String title) {
    return attribute("title", title);
  }

  public DomNode withClass(String clazz) {
    return attribute("class", clazz);
  }

  public DomNode attribute(String key) {
    if (key.isEmpty()) {
      return this;
    }
    return attribute(key, null);
  }

  public DomNode attribute(String key, Object value) {
    attributes.add(key);
    if (value == null) {
      attributes.add(null);
    } else {
      String s = value.toString();
      s = IS_NEWLINE.removeFrom(s);
      attributes.add(s);
    }
    return this;
  }

  public DomNode style(String key, String value) {
    return attribute("style", key + ": " + value + ";");
  }

  public DomNode data(String key, Object value) {
    attribute("data-" + key, value);
    return this;
  }

  public DomNode javascript(String name, boolean defer) {
    DomNode s = new DomNode("script").attribute("src", name);
    if (defer) {
      s.attribute("defer");
    }
    if (isDuplicateJS(s)) {
      return this;
    } else {
      return add(s);
    }
  }

  private boolean isDuplicateJS(DomNode s) {
    for (DomNode child : this.getChildren()) {
      try {
        if (!"script".equals(child.tag) || !child.hasAttribute("src")
            || !child.getAttribute("src").equals(s.getAttribute("src"))) {
          continue;
        }
      } catch (Exception e) {
        Log.debug("We are here.");
      }
      // checkState(child.hasAttribute("defer") == s.hasAttribute("defer"));
      return true;
    }
    return false;
  }

  public DomNode css(String name) {
    add(new DomNode("link").attribute("href", name).attribute("rel", "stylesheet"));
    return this;
  }

  public void render(StringBuilder sb, int depth) {
    renderStartTag(sb, depth);
    renderContent(sb, depth);
    renderEndTag(sb, depth);
    if (shouldGenWhitespace()) {
      sb.append("\n");
    }
  }

  public void renderStartTag(StringBuilder sb, int depth) {
    renderStartTag(sb, depth, Function.identity());
  }

  public void renderStartTag(StringBuilder sb, int depth, Function<String, String> valueFunction) {
    if (shouldGenWhitespace()) {
      for (int i = 0; i < depth; i++) {
        sb.append("  ");
      }
    }
    sb.append('<').append(tag);
    for (int i = 0; i < attributes.size(); i += 2) {
      String key = attributes.get(i);
      String value = valueFunction.apply(attributes.get(i + 1));
      sb.append(' ').append(key);
      if (value != null) {
        sb.append("=\"").append(value).append("\"");
      }
    }
    sb.append('>');

    if (shouldGenWhitespace() && hasRealChild()) {
      sb.append('\n');
    }
  }

  private boolean hasRealChild() {
    for (DomNode child : children) {
      if (!(child instanceof TextNode)) {
        return true;
      }
    }
    return false;
  }

  public void renderContent(StringBuilder sb, int depth) {
    for (DomNode child : children) {
      child.render(sb, depth + 1);
    }
  }

  public void renderEndTag(StringBuilder sb, int depth) {
    if (!children.isEmpty() || !tagsWithNoContent.contains(tag)) {
      if (shouldGenWhitespace() && hasRealChild()) {
        for (int i = 0; i < depth; i++) {
          sb.append("  ");
        }
      }
      sb.append("</").append(tag).append(">");
    }
    if (shouldGenWhitespace()) {
      sb.append('\n');
    }
  }

  private boolean shouldGenWhitespace() {
    if (this.generateWhitespace) {
      return true;
    }
    if (parent == null) {
      return false;
    }
    return parent.shouldGenWhitespace();
  }

  private static final Set<String> tagsWithNoContent = ImmutableSet.of("meta", "br", "img", "link", "import", "input");

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    render(sb, 0);
    return sb.toString();
  }

  public DomNode copy() {
    if (this instanceof TextNode) {
      return this;
    }
    DomNode ret = new DomNode(tag);
    copyInto(ret);
    return ret;
  }

  protected void copyInto(DomNode node) {
    node.attributes.addAll(attributes);
    node.generateWhitespace = generateWhitespace;
    for (DomNode child : children) {
      node.add(child.copy());
    }
  }

}
