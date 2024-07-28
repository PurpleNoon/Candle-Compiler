package com.asledgehammer.candle;

import com.asledgehammer.candle.impl.EmmyLuaRenderer;
import com.asledgehammer.candle.impl.PythonTypingsRenderer;
import com.asledgehammer.candle.impl.RosettaRenderer;
import zombie.Lua.LuaManager;
import zombie.iso.objects.interfaces.Thumpable;

import java.io.File;
import java.io.IOException;
import java.lang.annotation.Annotation;
import java.util.*;

class Candle {

  public static boolean addReturnClasses = false;
  final CandleGraph graph = new CandleGraph();

  public static boolean addSubClasses = false;
  public static boolean addSuperClasses = false;
  public static boolean addParameterClasses = false;

  void walk(boolean clear) {
    this.graph.walk(clear);
  }

  void render(CandleRenderAdapter adapter) {
    this.graph.render(adapter);
  }

  void save(File dir) throws IOException {
    saveJavaAPI(dir);
    saveGlobalAPI(dir);
    this.graph.save(dir);
  }

  void saveGlobalAPI(File dir) {
    CandleClass candleGlobalObject = this.graph.classes.get(LuaManager.GlobalObject.class);
    Map<String, CandleExecutableCluster<CandleMethod>> methods =
        new HashMap<>(candleGlobalObject.getStaticMethods());
    methods.putAll(candleGlobalObject.getMethods());

    List<String> keysSorted = new ArrayList<>(methods.keySet());
    keysSorted.sort(Comparator.naturalOrder());
    StringBuilder builder = new StringBuilder();
    for (String methodName : keysSorted) {
      CandleExecutableCluster<CandleMethod> cluster = methods.get(methodName);
      builder
          .append("\n")
          .append(
              cluster
                  .getRenderedCode()
                  .replaceAll("GlobalObject.", "")
                  .replaceAll("--- @public\n", "")
                  .replaceAll("--- @static\n", ""))
          .append("\n");
    }
    System.out.println("Candle: Writing __global.lua ..");
    CandleGraph.write(new File(dir, "__global.lua"), "--- @meta\n" + builder);
  }

  public void saveJavaAPI(File dir) {
    String fileContents =
        """
        --- @meta

        --- @alias byte number
        --- @alias short number
        --- @alias int number
        --- @alias char string
        --- @alias float number
        --- @alias double number
        --- @alias long number
        --- @alias void nil
        --- @alias Unknown Object
        --- @alias Object any
        --- @alias Void void
        --- @alias Boolean boolean
        --- @alias Short short
        --- @alias Integer int
        --- @alias Float float
        --- @alias Double double
        --- @alias Long long
        --- @alias BigInt number
        --- @alias Character string
        --- @alias String string
        --- @alias KahluaTable table
        ---
        --- @alias Map AbstractMap|EnumMap|HashMap
        --- @alias List AbstractList|AbstractSequentialList|ArrayList|LinkedList|Stack|Vector
        """;

    System.out.println("Candle: Writing __java.lua ..");
    CandleGraph.write(new File(dir, "__java.lua"), fileContents);
  }

  public static void main(String[] yargs) throws IOException {
    //    mainPy(yargs);
    mainLua(yargs);
  }

  private static void mainRosetta(String[] yargs) throws IOException {
    String path = "./dist/";
    if (yargs.length != 0) path = yargs[1];

    File dir = new File(path);
    if (!dir.exists() && !dir.mkdirs()) throw new IOException("Failed to mkdirs: " + path);

    Candle candle = new Candle();
    candle.walk(true);

    // Export to Rosetta
    RosettaRenderer renderer = new RosettaRenderer();
    candle.render(renderer);
    renderer.saveJSON(candle.graph, new File("./dist2/"));
  }

  private static void mainLua(String[] yargs) throws IOException {
    String path = "./dist/";
    if (yargs.length != 0) path = yargs[1];

    File dir = new File(path);
    if (!dir.exists() && !dir.mkdirs()) throw new IOException("Failed to mkdirs: " + path);

    Candle candle = new Candle();
    candle.graph.walkLegacy();

    // Export to Lua
    candle.render(new EmmyLuaRenderer());
    candle.save(dir);
  }

  private static void mainPy(String[] yargs) throws IOException {
    addSubClasses = true;
    addSuperClasses = true;
    addParameterClasses = true;
    addReturnClasses = true;

    String path = "./dist/";
    if (yargs.length != 0) path = yargs[0];

    File dir = new File(path);
    if (!dir.exists() && !dir.mkdirs()) throw new IOException("Failed to mkdirs: " + path);

    Candle candle = new Candle();
    candle.graph.walkEverything();
    candle.walk(false);

    //    System.out.println("Annotation: " + candle.graph.classes.containsKey(Annotation.class));

    // Export to Python
    PythonTypingsRenderer renderer = new PythonTypingsRenderer();
    renderer.render(candle.graph);
  }
}
