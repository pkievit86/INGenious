package com.ing.engine.commands.file;

import com.ing.datalib.testdata.model.TestDataModel;
import com.ing.engine.commands.browser.General;
import com.ing.engine.core.CommandControl;
import com.ing.engine.core.Control;
import com.ing.engine.support.Status;
import com.ing.engine.support.methodInf.Action;
import com.ing.engine.support.methodInf.InputType;
import com.ing.engine.support.methodInf.ObjectType;

import java.util.Arrays;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.io.FileNotFoundException;
import java.util.Collection;
import java.util.List;
import java.util.Scanner;
import java.io.File;
import java.io.PrintWriter;

import org.xmlunit.builder.DiffBuilder;
import org.xmlunit.builder.Input;
import org.xmlunit.diff.*;
import org.w3c.dom.Node;

import java.io.*;
import java.nio.file.*;


public class FileOperations extends General {

    public FileOperations(CommandControl cc) {
        super(cc);
    }

    @Action(object = ObjectType.FILE, desc = "Populate Data and Saving File", input = InputType.YES, condition = InputType.OPTIONAL)// MyFiles/
    public void populateData() {
        try {
            String fileName = getVar("%fileName%");
            String fileLocation = getVar("%fileLocation%");

            if (!fileLocation.endsWith("/")) {
                fileLocation += "/";
            }
            try (PrintWriter out = new PrintWriter(fileLocation + fileName))
            { out.println(handleFileContent(Data));
                Report.updateTestLog(Action,"File [" + fileName + "] is saved successfully in  " + fileLocation, Status.DONE);
            } catch (FileNotFoundException ex) {
                Logger.getLogger(FileOperations.class.getName()).log(Level.SEVERE, null, ex);
                Report.updateTestLog(Action, "Error Saving file in the directory :" + "\n" + ex.getMessage(),
                        Status.DEBUG);
            }
        } catch (Exception ex) {
            Logger.getLogger(FileOperations.class.getName()).log(Level.SEVERE, null, ex);
            Report.updateTestLog(Action, "Something went wrong in populating data and saving the file :" + "\n" + ex.getMessage(),
                    Status.DEBUG);
        }
    }

    @Action(object = ObjectType.FILE, desc = "Replace substring in File", input = InputType.YES, condition = InputType.OPTIONAL)// MyFiles/
    public void replace() {
        try {
            String fileName = getVar("%fileName%");
            String fileLocation = getVar("%fileLocation%");

            if (!fileLocation.endsWith("/")) {
                fileLocation += "/";
            }

            try {
                Path filePath = Paths.get(fileLocation + fileName);
                String content = new String(Files.readAllBytes(filePath));
                String[] parts = Data.split(",");

                String result = content.replace(parts[0], parts[1]);

                Files.write(filePath, result.getBytes());

                Report.updateTestLog(Action,"Replaced: " + parts[0] + " with " + parts[1] + " in [" + fileName + "] successfully in  " + fileLocation, Status.DONE);
            } catch (IOException ex) {
                Logger.getLogger(FileOperations.class.getName()).log(Level.SEVERE, null, ex);
                Report.updateTestLog(Action, "Error Saving file in the directory :" + "\n" + ex.getMessage(),
                        Status.DEBUG);
                throw new RuntimeException(ex);
            }
    } catch (Exception ex) {
            Logger.getLogger(FileOperations.class.getName()).log(Level.SEVERE, null, ex);
            Report.updateTestLog(Action, "Something went wrong in populating data and saving the file :" + "\n" + ex.getMessage(),
                    Status.DEBUG);
        }
    }



    private Boolean compareFiles(String file1Path, String file2Path) {

        boolean areEqual = true;

        try {
            BufferedReader reader1 = new BufferedReader(new FileReader(file1Path));
            BufferedReader reader2 = new BufferedReader(new FileReader(file2Path));

            String line1, line2;

            int lineNum = 1;

            while ((line1 = reader1.readLine()) != null | (line2 = reader2.readLine()) != null) {
                if (line1 == null || line2 == null || !line1.equals(line2)) {
                    areEqual = false;
                    System.out.println("Difference at line " + lineNum + ":");
                    System.out.println("File1: " + (line1 != null ? line1 : "EOF"));
                    System.out.println("File2: " + (line2 != null ? line2 : "EOF"));
                }
                lineNum++;
            }
            if (areEqual) {
                System.out.println("The files are identical.");
            }
            return areEqual;
        } catch (IOException e) {
                System.err.println("Error reading files: " + e.getMessage());
        }

        return areEqual;
    }


    @com.ing.engine.support.methodInf.Action(object = ObjectType.FILE, desc = "Compare Text files", input = InputType.YES, condition = InputType.NO)
    public void compareTextFiles() {
        try {
            String[] parts = Data.split(",");
            Boolean areEqual = compareFiles(parts[0],parts[1]);
            if (!areEqual) {
                Report.updateTestLog(Action, "Differences detected in Text files comparison, consult log for details", Status.DEBUG);
            } else {
                Report.updateTestLog(Action, "Text files compared successfully", Status.DONE);
            }
        } catch (Exception ex) {
            Logger.getLogger(this.getClass().getName()).log(Level.SEVERE, "Exception during Text files comparison", ex);
            Report.updateTestLog(Action, "Error in Text files comparison: " + "\n" + ex.getMessage(), Status.DEBUG);
        }
    }

    private String handleFileContent(String data) throws FileNotFoundException {
        String fileContent = data;
        File file = new File(Data);
        if (file.isFile()) {
            Scanner sc = new Scanner(file);
            fileContent = "";
            while (sc.hasNext()) {
                fileContent += sc.nextLine() + "\n";
            }
            sc.close();
        }
        fileContent = handleDataSheetVariables(fileContent);
        fileContent = handleuserDefinedVariables(fileContent);
        return fileContent;
    }

    private String handleDataSheetVariables(String fileContent) {
        List<String> sheetlist = Control.getCurrentProject().getTestData().getTestDataFor(Control.exe.runEnv())
                .getTestDataNames();
        for (int sheet = 0; sheet < sheetlist.size(); sheet++) {
            if (fileContent.contains("{" + sheetlist.get(sheet) + ":")) {
                TestDataModel tdModel = Control.getCurrentProject()
                        .getTestData().getTestDataByName(sheetlist.get(sheet));
                List<String> columns = tdModel.getColumns();
                for (int col = 0; col < columns.size(); col++) {
                    if (fileContent.contains("{" + sheetlist.get(sheet) + ":" + columns.get(col) + "}")) {
                        fileContent = fileContent.replace("{" + sheetlist.get(sheet) + ":" + columns.get(col) + "}",
                                userData.getData(sheetlist.get(sheet), columns.get(col)));
                    }
                }
            }
        }
        return fileContent;
    }

    private String handleuserDefinedVariables(String fileContent) {
        Collection<Object> valuelist = Control.getCurrentProject().getProjectSettings().getUserDefinedSettings()
                .values();
        for (Object prop : valuelist) {
            if (fileContent.contains("{" + prop + "}")) {
                fileContent = fileContent.replace("{" + prop + "}", prop.toString());
            }
        }
        return fileContent;
    }

}
