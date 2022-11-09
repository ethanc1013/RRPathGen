package jarhead;

import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.filechooser.FileSystemView;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.*;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.Scanner;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ButtonPanel extends JPanel {
    private static final Pattern numberPattern = Pattern.compile("[+-]?(\\d*\\.)?\\d+");
    private static final Pattern pathName = Pattern.compile("(?:^\\s*Trajectory\\s+(\\w*))");

    private final JButton exportButton = new JButton("Export");
    private final JButton importButton = new JButton("Import");
    public final JButton flipButton = new JButton("Flip");
    private final JButton clearButton = new JButton("Clear");
    private final JButton undoButton = new JButton("Undo");
    private final JButton redoButton = new JButton("Redo");
    private LinkedList<NodeManager> managers;
    private Main main;

    ButtonPanel(LinkedList<NodeManager> managers, Main main){
        this.main = main;
        this.managers = managers;
        this.setMinimumSize(new Dimension(0,20));
        this.setLayout(new GridLayout(1, 4, 1, 1));

        exportButton.setFocusable(false);
        importButton.setFocusable(false);
        flipButton.setFocusable(false);
        clearButton.setFocusable(false);
        undoButton.setFocusable(false);
        redoButton.setFocusable(false);
        this.add(exportButton);
        this.add(importButton);
        this.add(flipButton);
        this.add(clearButton);
        this.add(undoButton);
        this.add(redoButton);

        this.setVisible(true);

        exportButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                if(getCurrentManager().size() > 0){
                    main.infoPanel.editPanel.saveValues();
                    Node node = getCurrentManager().get(0);
                    double x = main.toInches(node.x);
                    double y = main.toInches(node.y);

                    String path = main.importPath;
                    File outputFile = new File(path.substring(0,path.length()-4) + "backup.java");
                    System.out.println(outputFile.getPath());
                    try {
                        outputFile.createNewFile();
                        FileWriter writer = new FileWriter(outputFile);
                        Scanner reader = new Scanner(new File(main.importPath));
                        
                        writer.close();
                    } catch (IOException ioException) {
                        ioException.printStackTrace();
                    }


                    System.out.printf("Trajectory %s = drive.trajectoryBuilder(new Pose2d(%.2f, %.2f, Math.toRadians(%.2f)))%n",getCurrentManager().name, x, -y, (node.splineHeading +90));
                    for (int i = 1; i < getCurrentManager().size(); i++) {
                        node = getCurrentManager().get(i);
                        x = main.toInches(node.x);
                        y = main.toInches(node.y);
                        switch (node.getType()){
                            case splineTo:
                                System.out.printf(".splineTo(new Vector2d(%.2f, %.2f), Math.toRadians(%.2f))%n", x, -y, (node.splineHeading +90));
                                break;
                            case displacementMarker:
                                System.out.printf(".splineTo(new Vector2d(%.2f, %.2f), Math.toRadians(%.2f))%n", x, -y, (node.splineHeading +90));
                                System.out.printf(".addDisplacementMarker(() -> {%s})%n", node.code);
                                break;
                            case splineToSplineHeading:
                                System.out.printf(".splineToSplineHeading(new Pose2d(%.2f, %.2f, Math.toRadians(%.2f)), Math.toRadians(%.2f))%n", x, -y, (node.robotHeading +90), (node.splineHeading +90));
                                break;
                            case splineToLinearHeading:
                                System.out.printf(".splineToLinearHeading(new Pose2d(%.2f, %.2f, Math.toRadians(%.2f)), Math.toRadians(%.2f))%n", x, -y, (node.robotHeading +90), (node.splineHeading +90));
                                break;
                            case splineToConstantHeading:
                                System.out.printf(".splineToConstantHeading(new Vector2d(%.2f, %.2f), Math.toRadians(%.2f))%n", x, -y, (node.splineHeading +90));
                                break;
                            default:
                                System.out.println("what");
                                break;
                        }
                    }
                    System.out.println(".build();");
                }
            }
        });

        flipButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                Node un = new Node(-2,-2);
                un.state = 3;
                for (int i = 0; i < getCurrentManager().size(); i++) {
                    Node node = getCurrentManager().get(i);
                    node.y = 144*main.scale-node.y;
                    node.splineHeading = 180-node.splineHeading;
                    getCurrentManager().set(i, node);
                }
                main.drawPanel.repaint();
            }
        });
        undoButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                main.undo();
                main.drawPanel.repaint();
            }
        });
        redoButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                main.redo();
                main.drawPanel.repaint();
            }
        });
        clearButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                //todo: add undo for this
                getCurrentManager().undo.clear();
                getCurrentManager().redo.clear();
                getCurrentManager().clear();
                int id = main.currentM;
                for (int i = id; i < managers.size()-1; i++) {
                    managers.set(i, managers.get(i+1));
                }
                if(managers.size() > 1)
                    managers.removeLast();
                else main.currentM = 0;
                if(main.currentM > 0)
                    main.currentM--;
                main.currentN = -1;
                main.infoPanel.editPanel.update();
                main.drawPanel.resetPath();

                main.drawPanel.renderBackgroundSplines();
                main.drawPanel.repaint();
            }
        });
        importButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                NodeManager manager = null;
                File file;
                if(main.prop.getProperty("IMPORT/EXPORT").matches("")){
                    JFileChooser chooser = new JFileChooser(FileSystemView.getFileSystemView());
                    FileNameExtensionFilter filter = new FileNameExtensionFilter("Java Files", "java");
                    chooser.setFileFilter(filter);
                    int r = chooser.showOpenDialog(null);
                    if(r != JFileChooser.APPROVE_OPTION) return;
                    main.importPath = chooser.getSelectedFile().getPath();
                    main.prop.setProperty("IMPORT/EXPORT", main.importPath);
                    main.saveConfig();
                    main.infoPanel.settingsPanel.update();
                    file = chooser.getSelectedFile();
                } else {
                    main.saveConfig();
                    file = new File("C:\\Users\\Jared\\StudioProjects\\OpenRC-Turbo\\TeamCode\\src\\main\\java\\org\\firstinspires\\ftc\\teamcode\\AutoRedLeft.java");
                }
                boolean discard = true;
                Import importer = new Import(main);
                LinkedList<NodeManager> in = importer.read(file);
                in.forEach((m) -> {
                    managers.add(m);
                });
                main.drawPanel.renderBackgroundSplines();
                main.drawPanel.repaint();

//                String line = "";
//                for (String[] data : in) {
//                    if (line.contains("trajectoryBuilder")){
//                        discard = false;
//                        Matcher matcher = pathName.matcher(line);
//                        matcher.find();
//                        String name = matcher.group(1).trim();
//                        if(getCurrentManager().size() > 0)
//                            manager = new NodeManager(new ArrayList<>(), managers.size(), name);
//                        else {
//                            manager = getCurrentManager();
//                            manager.name = name;
//                        }
//                        if(line.contains("true")) manager.reversed = true;
//                        managers.add(manager);
//                    }
//                    if(!discard){
//                        if(line.contains("new Vector2d(") || line.contains("new Pose2d(")){
//                            Matcher m = numberPattern.matcher(line);
//                            Node node = new Node();
//
//                            int i;
//                            for (i = 0; m.find(); i++) {
//                                data[i]=m.group(0);
//                            }
//                            String substring = line.trim().substring(1, line.trim().indexOf("("));
//                            try{
//                                int j = 0;
//                                if(substring.matches(".*\\d.*"))
//                                    j++;
//                                node.x = (Double.parseDouble(data[1+j])+72)* main.scale;
//                                node.y = (-Double.parseDouble(data[2+j])+72)* main.scale;
//                                if(i > 4 && j==0){
//                                    node.robotHeading = Double.parseDouble(data[3+j])-90;
//                                    node.splineHeading = Double.parseDouble(data[4+j])-90;
//                                } else {
//                                    node.splineHeading = Double.parseDouble(data[3+j])-90;
//                                    node.robotHeading = node.splineHeading;
//                                }
//                            } catch (Exception error){
//                                error.printStackTrace();
//                                node.x = 72* main.scale;
//                                node.y = 72* main.scale;
//                                node.splineHeading = 270;
//                                node.robotHeading = 270;
//                            }
//
//                            try {
//                                node.setType(Node.Type.valueOf(substring));
//                            } catch (IllegalArgumentException error){
//                                error.printStackTrace();
//                                node.setType(Node.Type.splineTo);
//                            }
//
//                            if(manager.reversed && manager.size() == 0) node.splineHeading += 180;
//                            manager.add(node);
//                        } else if(line.contains(".addDisplacementMarker(")){
//                            (manager.get(manager.size()-1)).setType(Node.Type.displacementMarker);
//                        } else {
//                            discard = true;
//                        }
//                    }
//                }
//
//                main.currentM = managers.size()-1;
//                main.infoPanel.editPanel.name.setText(getCurrentManager().name);
//                main.drawPanel.renderBackgroundSplines();
//                main.drawPanel.repaint();
            }
        });
    }

    private NodeManager getCurrentManager() {
        return main.getManagers().get(main.currentM);
    }
}
