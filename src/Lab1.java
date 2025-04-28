import java.io.*;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;

public class Lab1 {
    private final Map<String, Map<String, Integer>> directedGraph;
    private volatile boolean stopWalk = false;
    private static final double DAMPING_FACTOR = 0.85;
    private static final int MAX_ITERATIONS = 100;
    private static final double CONVERGENCE_THRESHOLD = 0.0001;
    private final CountDownLatch latch = new CountDownLatch(1);
    public Lab1() {
        directedGraph = new HashMap<>();
    }

    public static void main(String[] args){
        String filePath = "./test/test1.txt"; // 默认文本文件路径
        String dotFilePath = "./graph/directed_graph.dot"; // 输出的 DOT 文件路径
        String imageFilePath = "./graph/directed_graph.png";

        if(args.length>0){
            filePath = args[0];
        }
        Lab1 graph = new Lab1();

        String[] allwords = null;
        try {
            allwords = graph.showDirectedGraph(graph, filePath, dotFilePath, imageFilePath);
        } catch (IOException e) {
            e.printStackTrace();
        }
        String root = allwords[0];
        Scanner scanner = new Scanner(System.in);
        while (true) {
            try {
                while (System.in.available() > 0) {
                    scanner.nextLine();
                }
            } catch (IOException ignored) {

            }
            System.out.println("请选择操作：");
            System.out.println("1. 桥接词查询");
            System.out.println("2. 生成新文本");
            System.out.println("3. 计算两个单词之间最短路径");
            System.out.println("4. 计算PageRank");
            System.out.println("5. 开始随机游走");
            System.out.println("6. 退出");
            System.out.print("请输入操作编号：");
            int choice = 0;
            try {
                choice = scanner.nextInt();
                scanner.nextLine();
            } catch (Exception e) {
                System.out.println("输入无效，请输入一个整数！");
                scanner.nextLine();
            }
            graph.stopWalk = false;
            switch (choice) {
                case 0:
                    break;
                case 1:
                    System.out.println("当前操作->桥接词查询：");
                    System.out.print("请输入两个词语：");
                    String input = scanner.nextLine();
                    String[] words = input.split(" ");
                    if (words.length != 2) {
                        System.out.println("要求输入词语数量为2！");
                        break;
                    }
                    String word1 = words[0].toLowerCase();
                    String word2 = words[1].toLowerCase();
                    Set<String> bridgeWords = graph.queryBridgeWords(word1, word2, true);
                    break;
                case 2:
                    System.out.println("当前操作->根据bridge word生成新文本：");
                    System.out.print("请输入文本：");
                    String input_sentence = scanner.nextLine();
                    graph.generateNewText(input_sentence);
                    break;

                case 3:
                    System.out.println("当前操作->计算两个单词之间最短路径：");
                    System.out.print("请输入两个词语：");
                    String input_words = scanner.nextLine();
                    String[] wordss = input_words.split(" ");
                    AtomicBoolean flag = new AtomicBoolean(true);
                    if (wordss.length == 1) {
                        // 生成到所有节点的最短路径
                        String word11 = wordss[0].toLowerCase();
                        int index = 0;
                        for (String word22 : allwords) {
                            if(!flag.get()){
                                break;
                            }
                            graph.calcShortestPath(word11, word22, ++index, root, flag);
                        }
                    } else if (wordss.length != 2) {
                        System.out.println("要求输入词语数量为2！");
                    } else {
                        String word11 = wordss[0].toLowerCase();
                        String word22 = wordss[1].toLowerCase();
                        graph.calcShortestPath(word11, word22, 1, root,flag);
                    }
                    break;

                case 4:
                    System.out.println("当前操作->请输入要查询的单词");
                    String input_pr = scanner.nextLine();
                    Double pr = graph.calPageRank(input_pr);
                    if (pr == null) {
                        System.out.printf("单词%s不在图中\n", input_pr);
                    } else {
                        System.out.printf("单词%s的pr值为%f\n", input_pr, pr);
                    }
                    break;
                case 5:
                    System.out.println("当前操作->开始随机游走");
                    Thread walkThread = new Thread(() -> {
                        graph.randomWalk();
                        graph.latch.countDown(); // 通知主线程
                    });
                    walkThread.start();

                    System.out.println("输入任意键以停止随机游走：");
                    try {
                        while (walkThread.isAlive()) {
                            if (System.in.available() > 0) {
                                graph.stopWalk = true;
                                break;
                            }
                        }
                    } catch (IOException e) {
                        e.printStackTrace();
                    }

                    try {
                        walkThread.join();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    break;

                case 6:
                    System.out.println("当前操作->退出程序");
                    scanner.close();
                    return;

                default:
                    System.out.println("无效的操作编号，请重新输入！");
            }
        }
    }

    public String[] showDirectedGraph(Lab1 graph, String filePath, String dotFilePath, String imageFilePath) throws IOException {
        System.out.println("开始生成有向图");
        String[] words = graph.buildDirectedGraph(filePath); // 构建有向图
        graph.createDotFile(dotFilePath,null,words[0]); // 生成 DOT 文件
        convertDotToImage(dotFilePath, imageFilePath); // 将 DOT 文件转换为图像文件
        return words;
    }

    public void createDotFile(String dotFilePath, List<List<Object>> shortestPaths, String root) {
        try (PrintWriter writer = new PrintWriter(dotFilePath)) {
            writer.println("digraph G {");
            // 将根节点标记为根节点
            writer.println("\t" + root + " [style=filled, fillcolor=lightgray];");
            for (Map.Entry<String, Map<String, Integer>> entry : directedGraph.entrySet()) {
                String vertex = entry.getKey();
                Map<String, Integer> edges = entry.getValue();
                for (Map.Entry<String, Integer> edge : edges.entrySet()) {
                    String destination = edge.getKey();
                    int weight = edge.getValue();
                    writer.println("\t" + vertex + " -> " + destination + " [label=\"" + weight + "\"];");
                }
            }
            // 根据最短路径添加不同颜色的属性
            if (shortestPaths == null) {
                writer.println("}");
                return;
            }
            int colorIndex = 0;
            String[] colors = {"red", "green", "blue", "yellow", "orange", "purple"}; // 可以自定义颜色
            for (List<Object> pathWithLength : shortestPaths) {
                String color = colors[colorIndex % colors.length]; // 循环使用颜色
                List<String> path = (List<String>) pathWithLength.get(0);
                for (int i = 0; i < path.size() - 1; i++) {
                    writer.println("\t" + path.get(i) + " -> " + path.get(i + 1) + " [color=\"" + color + "\", penwidth=2];");
                }
                colorIndex++;
            }
            writer.println("}");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void convertDotToImage(String dotFilePath, String imageFilePath) {
        try {
            ProcessBuilder processBuilder = new ProcessBuilder("dot", "-Tpng", dotFilePath, "-o", imageFilePath);
            Process process = processBuilder.start();
            int exitCode = process.waitFor();
            if (exitCode == 0) {
                System.out.println("有向图生成成功: " + imageFilePath);
            } else {
                System.out.println("有向图生成失败");
            }
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
        }
    }

    public String[] buildDirectedGraph(String filePath) throws IOException {
        StringBuilder content = new StringBuilder();
        boolean rootSet = false; // 标志是否已经设置根节点
        String rootWord = null; // 根节点的单词
        try (BufferedReader br = new BufferedReader(new FileReader(filePath))) {
            String line;
            while ((line = br.readLine()) != null) {
                content.append(line).append(" "); // 将每行内容添加到 content 中，并添加空格作为单词间的分隔符
            }
        }
        // 将非字母字符替换为空格，并转换为小写
        String processedContent = content.toString().replaceAll("[^a-zA-Z\\n\\r]", " ").toLowerCase();
        String[] words = processedContent.split("\\s+"); // 按空格分割单词

        for (int i = 0; i < words.length - 1; i++) {
            String currentWord = words[i];
            //System.out.println("currentWord: " + currentWord);
            String nextWord = words[i + 1];
            addNode(currentWord);
            addNode(nextWord);
            addEdge(currentWord, nextWord);
        }

        return words;
    }

    public void addNode(String node) {
        directedGraph.putIfAbsent(node, new HashMap<>());
    }

    public void addEdge(String source, String destination) {
        directedGraph.get(source).merge(destination, 1, Integer::sum);
    }

    public Set<String> queryBridgeWords(String start, String end, Boolean print) {
        List<String> path = new ArrayList<>();
        if (!directedGraph.containsKey(start) && print) {
            System.out.println("在图中没有“" + start +"”");
            return null;
        }
        if (!directedGraph.containsKey(end) && print) {
            System.out.println("在图中没有“" + end +"”");
            return null;
        }
        // 广度优先搜索搜索桥接词
        Set<String> bridgeWords = new HashSet<>();
        Set<String> visited = new HashSet<>();
        Queue<String> queue = new LinkedList<>();
        queue.offer(start);
        visited.add(start);
        String current = queue.poll();
        for (Map.Entry<String, Integer> entry : directedGraph.getOrDefault(current, Collections.emptyMap()).entrySet()) {
            String neighbor = entry.getKey();
            if (!visited.contains(neighbor)) {
                visited.add(neighbor);
                queue.offer(neighbor);
                if (directedGraph.containsKey(neighbor) && directedGraph.get(neighbor).containsKey(end)) {
                    bridgeWords.add(neighbor);
                    if (print) {
                        System.out.println("桥接词为：" + neighbor);
                    }
                }
            }
        }
        if (bridgeWords.isEmpty() && print) {
            System.out.println(start + "和" + end + "之间没有桥接词");
        }
        return bridgeWords;
    }

    public String generateNewText(String inputText) {
        StringBuilder newText = new StringBuilder();
        String[] words = inputText.split(" ");
        Random random = new Random(); // 创建随机数生成器

        for (int i = 0; i < words.length - 1; i++) {
            String currentWord = words[i];
            String nextWord = words[i + 1];

            // 将当前单词添加到新文本中
            newText.append(currentWord).append(" ");

            // 查询当前单词和下一个单词之间的桥接词
            Set<String> bridgeWords = queryBridgeWords(currentWord, nextWord, false);
            if (bridgeWords != null && !bridgeWords.isEmpty()) {
                // 如果存在桥接词，则随机选择一个插入到新文本中
                List<String> bridgeWordsList = new ArrayList<>(bridgeWords);
                String randomBridge = bridgeWordsList.get(random.nextInt(bridgeWordsList.size()));
                newText.append(randomBridge).append(" ");
            }
        }

        // 将最后一个单词添加到新文本中
        newText.append(words[words.length - 1]);

        // 打印生成的新文本
        System.out.println("生成的新文本为: " + newText.toString());

        return newText.toString();
    }

    public List<List<Object>> calcShortestPath(String word1, String word2, int i, String root,AtomicBoolean flag) {
        List<String> path = new ArrayList<>();
        Set<String> visited = new HashSet<>();
        if (!directedGraph.containsKey(word1)) {
            System.out.println("在图中没有“" + word1 +"”");
            flag.set(false);
            return null;
        }
        List<List<Object>> allPaths = findAllPaths(word1, word2, visited, path, 0);
        if (allPaths.isEmpty()) {
            return null;
        }
        System.out.println("The path between " + word1 + " " + word2 + " is: ");
        int minLength = Integer.MAX_VALUE;
        for (List<Object> p : allPaths) {
            int length = (int) p.get(1);
            if (length < minLength) {
                minLength = length;
            }
            System.out.println(p);
        }
        List<List<Object>> shortestPaths = new ArrayList<>();
        for (List<Object> p : allPaths) {
            int length = (int) p.get(1);
            if (length == minLength) {
                shortestPaths.add(p);
            }
        }
        //生成文件后缀加上i
        String dotFilePath = "./graph/directed_graph_shortest" + i + ".dot";
        String imageFilePath = "./graph/directed_graph_shortest" + i + ".png";
        // 生成 DOT 文件并标记最短路径为不同颜色
        createDotFile(dotFilePath, shortestPaths, root);
        // 转换 DOT 文件为图像文件
        convertDotToImage(dotFilePath, imageFilePath);
        return shortestPaths;
    }
    private List<List<Object>> findAllPaths(String current, String end, Set<String> visited, List<String> path, int currentLength) {
        // 如果当前节点是目标节点，则将当前路径加入结果集合中
        if (current.equals(end)) {
            List<String> newPath = new ArrayList<>(path);
            newPath.add(current);
            List<Object> resultPath = new ArrayList<>();
            resultPath.add(newPath);
            resultPath.add(currentLength);
            List<List<Object>> result = new ArrayList<>();
            result.add(resultPath);
            return result;
        }

        // 将当前节点标记为已访问
        visited.add(current);

        // 初始化结果集合
        List<List<Object>> allPaths = new ArrayList<>();

        // 遍历当前节点的邻居节点
        for (Map.Entry<String, Integer> entry : directedGraph.getOrDefault(current, Collections.emptyMap()).entrySet()) {
            String neighbor = entry.getKey();

            // 如果邻居节点未被访问，则递归查找从邻居节点到目标节点的所有路径
            if (!visited.contains(neighbor)) {
                path.add(current);
                List<List<Object>> neighborPaths = findAllPaths(neighbor, end, visited, path, currentLength + entry.getValue());
                allPaths.addAll(neighborPaths);
                path.remove(path.size() - 1); // 移除当前节点，以备其他路径使用
            }
        }

        visited.remove(current);

        return allPaths;
    }

    public Double calPageRank(String word){
        if (!directedGraph.containsKey(word)) {
            return null;
        }
        int N = directedGraph.size();
        Map<String, Double> currentScores = new HashMap<>();
        Map<String, Double> pageRankScores;
        // 初始化

//        double initialValue = 1.0 / N;
//        for (String w : directedGraph.keySet()) {
//            currentScores.put(w, initialValue);
//        }
        Map<String, Double> tfIdfScores = calculateTfIdfScores();
        double sum = tfIdfScores.values().stream().mapToDouble(Double::doubleValue).sum();
        for (Map.Entry<String, Double> entry : tfIdfScores.entrySet()) {
            String w = entry.getKey();
            Double score = entry.getValue();
            currentScores.put(w, score / sum);
        }

        for (int iter = 0; iter < MAX_ITERATIONS; iter++) {
            Map<String, Double> newScores = new HashMap<>();
            double baseScore = (1.0 - DAMPING_FACTOR) / N;

            for (String node : directedGraph.keySet()) {
                double total = 0.0;
                for (Map.Entry<String, Map<String, Integer>> stringMapEntry : directedGraph.entrySet()) {
                    if (stringMapEntry.getValue().containsKey(node)) {
                        String referringNode = stringMapEntry.getKey();
                        int outDegree = stringMapEntry.getValue().size();
                        double v = currentScores.get(referringNode) / outDegree;
                        total += v;
                    }
                }
                newScores.put(node, baseScore + DAMPING_FACTOR * total);
            }

            if (hasConverged(currentScores, newScores)) {
                break;
            }
            currentScores = newScores;
        }
        pageRankScores = currentScores;
        return pageRankScores.get(word);
    }


    private Map<String, Double> calculateTfIdfScores() {
        // 这里需要你提供原始文本内容或词频统计
        // 简化实现：使用节点出度作为重要性指标
        Map<String, Double> tfIdfScores = new HashMap<>();
        directedGraph.forEach((word, edges) -> {
            double score = edges.values().stream().mapToInt(Integer::intValue).sum();
            tfIdfScores.put(word, score);
        });
        return tfIdfScores;
    }

    private boolean hasConverged(Map<String, Double> oldScores,
                                 Map<String, Double> newScores) {
        for (String node : oldScores.keySet()) {
            if (Math.abs(oldScores.get(node) - newScores.get(node)) > CONVERGENCE_THRESHOLD) {
                return false;
            }
        }
        return true;
    }

    public String randomWalk() {
        // 选择随机起点
        Random random = new Random();
        List<String> vertices = new ArrayList<>(directedGraph.keySet());
        String currentVertex = vertices.get(random.nextInt(vertices.size()));

        StringBuilder randomWalkText = new StringBuilder();
        // 记录遍历的节点和边
        List<String> visitedVertices = new ArrayList<>();
        List<String> visitedEdges = new ArrayList<>();

        // 开始随机游走
        while (!stopWalk) {
            visitedVertices.add(currentVertex);
            Map<String, Integer> edges = directedGraph.get(currentVertex);
            if (edges != null && !edges.isEmpty()) {
                List<String> nextVertices = new ArrayList<>(edges.keySet());
                String nextVertex = nextVertices.get(random.nextInt(nextVertices.size()));
                String edge = currentVertex + " -> " + nextVertex;
                visitedEdges.add(edge);
                currentVertex = nextVertex;

                // 模拟延迟以便于演示
                try {
                    Thread.sleep(500);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            } else {
                break; // 当前节点没有出边，结束随机游走
            }
        }

        // 将遍历的节点输出为文本，并以文件形式写入磁盘
        String outputPath = "./random_walk.txt";
        try (PrintWriter writer = new PrintWriter(new FileWriter(outputPath))) {
            writer.println("随机游走的节点:");
            for (String vertex : visitedVertices) {
                randomWalkText.append(vertex).append(" ");
            }
            writer.println(randomWalkText);

            System.out.println("随机游走的结果已写入文件：" + outputPath);
        } catch (IOException e) {
            e.printStackTrace();
        }

        return outputPath;
    }
}
