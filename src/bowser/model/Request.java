package bowser.model;

import static java.lang.Integer.parseInt;
import static java.lang.Long.parseLong;
import static ox.util.Utils.only;
import static ox.util.Utils.propagate;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.simpleframework.http.Cookie;
import org.simpleframework.http.Part;
import org.simpleframework.http.Path;
import org.simpleframework.http.Query;

import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;

import bowser.handler.MobileDetector;
import ox.Json;
import ox.Pair;
import ox.util.Images;

public class Request {

  public final org.simpleframework.http.Request request;
  public List<String> segments;
  public String path;
  private Map<String, Object> userData = Maps.newHashMap();

  private String host = null;

  public Request(org.simpleframework.http.Request request) {
    this.request = request;
    setPath(request.getPath().getPath());
  }

  public void setHost(String s) {
    this.host = s;
  }

  public String getHost() {
    if (host == null) {
      String s = getHeader("Host");
      if (s != null) {
        int i = s.indexOf(':');
        if (i != -1) {
          s = s.substring(0, i);
        }
        host = s;
      }
    }
    return host;
  }

  public String getIP() {
    return request.getClientAddress().getAddress().getHostAddress();
  }

  public String getOriginalPath() {
    return request.getPath().getPath();
  }

  public Path getPath() {
    return request.getPath();
  }

  public void setPath(String path) {
    this.segments = Splitter.on('/').omitEmptyStrings().splitToList(path);
    this.path = path.toLowerCase();
  }

  public String getSegment(int index) {
    return segments.get(index);
  }

  public int getInt(int index) {
    return parseInt(getSegment(index));
  }

  public long getLong(int index) {
    return parseLong(getSegment(index));
  }

  public boolean isPost() {
    return request.getMethod().equalsIgnoreCase("POST");
  }

  public String getMethod() {
    return request.getMethod();
  }

  public String param(String key) {
    return request.getQuery().get(key);
  }

  public Query getQuery() {
    return request.getQuery();
  }

  public String cookie(String key) {
    Cookie cookie = request.getCookie(key);
    return cookie == null ? null : cookie.getValue();
  }

  @SuppressWarnings("unchecked")
  public <T> T get(String key) {
    return (T) userData.get(key);
  }

  public Request put(String key, Object value) {
    if (value != null) {
      userData.put(key, value);
    }
    return this;
  }

  public Json getJson() {
    String s = getContent();
    if (s.isEmpty()) {
      return Json.object();
    }
    return new Json(s);
  }

  public String getContent() {
    try {
      return request.getContent();
    } catch (IOException e) {
      throw propagate(e);
    }
  }

  @Override
  public String toString() {
    return getMethod() + " " + path;
  }

  public String getHeader(String key) {
    return request.getValue(key);
  }

  public boolean isAjax() {
    if (!getMethod().equals("GET")) {
      return true;
    }
    String contentType = getHeader("Content-Type");
    if (contentType != null && contentType.startsWith("application/x-www-form")) {
      return true;
    }
    return false;
  }

  public HttpFile getFile() {
    return only(getFiles());
  }

  public HttpFile getFile(String fileName) {
    Part part = request.getPart(fileName);
    return part == null ? null : new HttpFile(part);
  }

  public List<HttpFile> getFiles() {
    List<HttpFile> ret = Lists.newArrayList();
    for (Part part : request.getParts()) {
      ret.add(new HttpFile(part));
    }
    return ret;

  }

  public Map<String, String> getHeaders() {
    Map<String, String> ret = Maps.newLinkedHashMap();
    request.getNames().forEach(name -> {
      ret.put(name, request.getValue(name));
    });
    return ret;
  }

  public Pair<Long, Long> getRange() {
    String s = request.getValue("Range");
    if (s == null || !s.startsWith("bytes=")) {
      return null;
    }
    s = s.substring(6);
    int index = s.indexOf('-');
    long start = parseLong(s.substring(0, index));
    Long end = null;
    if (index < s.length() - 1) {
      end = parseLong(s.substring(index + 1));
    }
    return Pair.of(start, end);
  }

  private static final Set<String> staticExtensions;
  static {
    Set<String> set = Sets.newHashSet("css", "scss", "js", "mjs", "ico", "otf", "woff", "woff2", "eot", "ttf",
        "mp4", "map", "pdf", "cur", "txt", "mp3", "mov", "webm");
    set.addAll(Images.FORMATS);
    staticExtensions = ImmutableSet.copyOf(set);
  }

  public boolean isStaticResource() {
    int i = path.lastIndexOf(".");
    if (i == -1) {
      return false;
    }
    String extension = path.substring(i + 1);
    return staticExtensions.contains(extension);
  }

  public boolean isFromMobile() {
    return MobileDetector.isFromMobile(this);
  }
}
