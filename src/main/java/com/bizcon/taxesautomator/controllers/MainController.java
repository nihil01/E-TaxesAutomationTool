package com.bizcon.taxesautomator.controllers;

import com.bizcon.taxesautomator.models.Record;
import com.bizcon.taxesautomator.services.ExcelService;
import com.bizcon.taxesautomator.utils.AlertorFX;
import com.bizcon.taxesautomator.services.ApiService;
import com.bizcon.taxesautomator.utils.CheckVariants;
import com.bizcon.taxesautomator.utils.UiModifier;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.control.cell.CheckBoxTableCell;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.control.cell.TextFieldTableCell;
import javafx.scene.layout.AnchorPane;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;

import java.io.*;
import java.net.URL;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.prefs.Preferences;
import java.util.stream.Collectors;

public class MainController extends ApiService implements Initializable, UiModifier {

    @FXML
    private TextArea folderTextArea;

    @FXML
    private TextArea voenTextArea;

    @FXML
    private Button folderBtn;

    @FXML
    private Button voenBtn;

    @FXML
    private TableView<Record> mainTable;

    @FXML
    private Button updTable;

    @FXML
    private Button startScript;

    @FXML
    private AnchorPane overlayPane;

    @FXML
    private ProgressIndicator progressIndicator;

    @FXML
    private TabPane mainTabPane;

    private final DirectoryChooser directoryChooser = new DirectoryChooser();
    private final FileChooser fileChooser = new FileChooser();
    private final ObservableList<Record> records = FXCollections.observableArrayList();
    private final Preferences prefs = Preferences.userNodeForPackage(MainController.class);
    private Map<String, String> rowStyles = new HashMap<>();

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        this.uiModifier = this;
        try {
            checkPreferences();
        } catch (IOException e) {
            AlertorFX.show("Xeta: Excel fayli yuklemek mumkun olmadi!", Alert.AlertType.WARNING);
        }
        initializeTableView();
        setupRowFactory();

        progressIndicator.setProgress(ProgressIndicator.INDETERMINATE_PROGRESS);
        progressIndicator.setPrefSize(100, 100);

        folderBtn.setOnAction(event -> {
            File folder = directoryChooser.showDialog(folderBtn.getScene().getWindow());
            if (folder != null) {
                folderTextArea.setText(folder.getAbsolutePath());
                prefs.put("folderSavePath", folder.getAbsolutePath());
            }
        });

        voenBtn.setOnAction(event -> {
            File file = fileChooser.showOpenDialog(folderBtn.getScene().getWindow());
            if (file != null && file.canRead()
                    && file.isFile() &&
                    (file.getName().endsWith(".xlsx") || file.getName().endsWith(".xls")) && file.getTotalSpace() > 0) {
                voenTextArea.setText(file.getAbsolutePath());
                prefs.put("voenSavePath", file.getAbsolutePath());

            }else{
                AlertorFX.show("Fayl bosdur ve ya .xlsx/.xls formatda deyil!", Alert.AlertType.ERROR);

            }
        });

        updTable.setOnAction(actionEvent -> {
            if (prefs.get("voenSavePath", null) != null){
                AlertorFX.show("Cedvel yenilendi!", Alert.AlertType.CONFIRMATION);
                try {
                    List<Record> data = filterRecordList(ExcelService.readExcelFile(prefs.get("voenSavePath", null)));
                    records.setAll(data);
                } catch (IOException e) {
                    AlertorFX.show("Fayl secilmeyib! Xeta!", Alert.AlertType.WARNING);
                }

            }else{
                AlertorFX.show("Fayl secilmeyib!", Alert.AlertType.WARNING);
            }
        });


        startScript.setOnAction(actionEvent -> {

            if (prefs.get("folderSavePath", null) == null){
                AlertorFX.show("Papka mutleq secilmelidir!", Alert.AlertType.WARNING);
                return;
            }

            AlertorFX.show("Emeliyyat ugurla basladi! Cedvelde progresi gore bilersiniz", Alert.AlertType.INFORMATION);
            // Check if record fields are valid according to Azerbaijan standards
            List<Record> recordList = readRecords();
            for (Record record : recordList) {
                try {
                    if (checkRecord(record)){

                        String startDate, endDate;

                        if (!record.getOxunmamis().get()){
                            System.out.println("Oxunmamis ! So i proceed to check");

                            startDate = checkDate(record.getBaslangicTarixi(), CheckVariants.CHECK_PARAMETERS);
                            endDate = checkDate(record.getBitmeTarixi(), CheckVariants.CHECK_PARAMETERS);

                            if (startDate == null || endDate == null){
                                AlertorFX.show("Tarixi duzgun daxil edin!", Alert.AlertType.ERROR);
                                return;
                            }

                        }else{

                            String[] dateOutputs = checkDate(null,
                                CheckVariants.GET_DEFAULTS).split(";");

                            startDate = dateOutputs[0];
                            endDate = dateOutputs[1];

                            System.out.println("Oxunmus! So i skip the chek and pass defaults !!");

                        }

                        System.out.println("Baslangic tarixi: " + startDate);
                        System.out.println("Bitme tarixi: " + endDate);

                        mainTable.setEditable(false);
                        showProgressLoader(true);

                        prepareApiCalls(recordList.size(), record.getAsanNomre(), record.getAsanId(),
                                record.getSearchStatus(), startDate, endDate, record.getOxunmamis().get(),
                                record.getMakeReport().get());

                    }else{
                        AlertorFX.show("Cedvelde sehv var! " + "ID: " + record.getAsanId() + "& Nomre: "
                                + record.getAsanNomre(), Alert.AlertType.ERROR);
                    }
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
                }
        });
    }


    //UI INITIALIZATION

    private void checkPreferences() throws IOException {
        String folderSavePath = prefs.get("folderSavePath", null);
        String voenSavePath = prefs.get("voenSavePath", null);

        if (folderSavePath != null) {
            folderTextArea.setText(folderSavePath);
        }

        if (voenSavePath != null) {
            voenTextArea.setText(voenSavePath);
            List<Record> data = filterRecordList(ExcelService.readExcelFile(voenSavePath));
            records.addAll(data);
        }
    }

    private List<Record> readRecords() {

        List<Record> recordList = mainTable.getItems().stream().toList();

        if (recordList.isEmpty()) {
            AlertorFX.show("Cedvel bosdur!", Alert.AlertType.WARNING);
        }else{
            return recordList;
        }

        System.out.println(recordList);
        return recordList;
    }

    private void initializeTableView(){
        mainTable.setEditable(true);

        TableColumn<Record, String> voenColumn = new TableColumn<>("VÖEN");
        TableColumn<Record, String> searchStatusColumn = new TableColumn<>("Axtarış statusu");
        TableColumn<Record, String> asanNomreColumn = new TableColumn<>("ASAN nömrəsi");
        TableColumn<Record, String> asanIdColumn = new TableColumn<>("ASAN ID");
        TableColumn<Record, Boolean> oxunmamisColumn = new TableColumn<>("Oxunmamış");
        TableColumn<Record, Boolean> personalReportColumn = new TableColumn<>("Şəxsi hesabi");
        TableColumn<Record, String> baslangicTarixiColumn = new TableColumn<>("Başlanğıc tarixi");
        TableColumn<Record, String> bitmeTarixiColumn = new TableColumn<>("Bitmə tarixi");

        voenColumn.setCellValueFactory(new PropertyValueFactory<>("voen"));
        voenColumn.setCellFactory(TextFieldTableCell.forTableColumn());
        voenColumn.setOnEditCommit(event ->
                event.getRowValue().setVoen(event.getNewValue()));

        searchStatusColumn.setCellValueFactory(new PropertyValueFactory<>("searchStatus"));
        searchStatusColumn.setCellFactory(TextFieldTableCell.forTableColumn());
        searchStatusColumn.setOnEditCommit(event ->
                event.getRowValue().setSearchStatus(event.getNewValue()));

        asanNomreColumn.setCellValueFactory(new PropertyValueFactory<>("asanNomre"));
        asanNomreColumn.setCellFactory(TextFieldTableCell.forTableColumn());
        asanNomreColumn.setOnEditCommit(event ->
                event.getRowValue().setAsanNomre(event.getNewValue()));

        asanIdColumn.setCellValueFactory(new PropertyValueFactory<>("asanId"));
        asanIdColumn.setCellFactory(TextFieldTableCell.forTableColumn());
        asanIdColumn.setOnEditCommit(event ->
                event.getRowValue().setAsanId(event.getNewValue()));

        oxunmamisColumn.setCellValueFactory(cellData -> cellData.getValue().getOxunmamis());
        oxunmamisColumn.setCellFactory(tc -> new CheckBoxTableCell<>());
        oxunmamisColumn.setOnEditCommit(event ->
                event.getRowValue().setOxunmamis(event.getNewValue()));

        personalReportColumn.setCellValueFactory(cellData -> cellData.getValue().getMakeReport());
        personalReportColumn.setCellFactory(tc -> new CheckBoxTableCell<>());
        personalReportColumn.setOnEditCommit(event ->
                event.getRowValue().setMakeReport(event.getNewValue()));

        baslangicTarixiColumn.setCellValueFactory(new PropertyValueFactory<>("baslangicTarixi"));
        baslangicTarixiColumn.setCellFactory(TextFieldTableCell.forTableColumn());
        baslangicTarixiColumn.setOnEditCommit(event ->
                event.getRowValue().setBaslangicTarixi(event.getNewValue()));

        bitmeTarixiColumn.setCellValueFactory(new PropertyValueFactory<>("bitmeTarixi"));
        bitmeTarixiColumn.setCellFactory(TextFieldTableCell.forTableColumn());
        bitmeTarixiColumn.setOnEditCommit(event ->
                event.getRowValue().setBitmeTarixi(event.getNewValue()));

        mainTable.getColumns().addAll(voenColumn, searchStatusColumn, asanNomreColumn,
                asanIdColumn, oxunmamisColumn, personalReportColumn, baslangicTarixiColumn, bitmeTarixiColumn);

        mainTable.setItems(records);
    }

    private List<Record> filterRecordList(List<Record> records){
        return records.stream().filter(record ->
            record.getVoen() != null && !record.getVoen().isEmpty() &&
                record.getOxunmamis() != null  &&
                record.getMakeReport() != null &&
                record.getBaslangicTarixi() != null && !record.getBaslangicTarixi().isEmpty() &&
                record.getBitmeTarixi() != null && !record.getBitmeTarixi().isEmpty() &&
                record.getSearchStatus() != null && !record.getSearchStatus().isEmpty() &&
                record.getAsanNomre() != null && !record.getAsanNomre().isEmpty() &&
                record.getAsanId() != null && !record.getAsanId().isEmpty()
        ).collect(Collectors.toList());
    }



    private void showProgressLoader(boolean state) {

        if (state){
            Tab settingsTab = mainTabPane.getTabs().getFirst();
            System.out.println("settings tab");
            System.out.println(settingsTab);

            mainTabPane.getSelectionModel().select(settingsTab);
        }

        overlayPane.setVisible(state);
    }


    //UI ModifyMethods
    @Override
    public void markAsFailed(String asanId) {
        Platform.runLater(() -> {
            rowStyles.put(asanId, "-fx-background-color: #FFB6C1;"); // light red
            mainTable.refresh();
        });
    }

    @Override
    public void markAsCompleted(String asanId) {
        Platform.runLater(() -> {
            rowStyles.put(asanId, "-fx-background-color: #90EE90;"); // light green
            mainTable.refresh();
        });
    }

    @Override
    public void notifyCompletion(String message) {
        Platform.runLater(() -> {
            AlertorFX.show(message, Alert.AlertType.INFORMATION);
            showProgressLoader(false);
            mainTable.setEditable(true);
        });
    }

    private void setupRowFactory() {
        mainTable.setRowFactory(tv -> new TableRow<>() {
            @Override
            protected void updateItem(Record item, boolean empty) {
                super.updateItem(item, empty);
                if (item == null || empty) {
                    setStyle("");
                } else {
                    setStyle(rowStyles.getOrDefault(item.getAsanId(), ""));
                }
            }
        });
    }

    //check for input values

    private boolean checkRecord(Record record) {
        if (record.getAsanId() == null || record.getAsanNomre() == null ||
                record.getAsanId().trim().isEmpty() || record.getAsanNomre().trim().isEmpty()) {
            return false;
        }

        try {
            Integer.parseInt(record.getAsanId());
        } catch (NumberFormatException e) {
            return false;
        }

        String mobile = record.getAsanNomre().trim();
        if (!mobile.startsWith("+994") || mobile.length() != 13) {
            return false;
        }

        try {
            Long.parseLong(mobile.substring(4));
        } catch (NumberFormatException e) {
            return false;
        }

        return true;
    }

    private String checkDate(String inputDateString, CheckVariants prop){

        if (prop == CheckVariants.CHECK_PARAMETERS){
            try {

                LocalDate.parse(inputDateString, DateTimeFormatter.ofPattern("dd.MM.yyyy"));
                System.out.println("Valid time string: " + inputDateString);

                List<String> listString = Arrays.asList(inputDateString.split("\\."));
                Collections.reverse(listString);

                return String.join("-", listString);

            } catch (DateTimeParseException | NullPointerException e) {
                System.out.println("Invalid time string: " + inputDateString);
                return null;
            }
        }else{

            LocalDate dateNow = LocalDate.now();

            String[] dateArray = {
              "%s-%02d-%02d".formatted(dateNow.getYear(), 1, 1), "%s-%02d-%02d".formatted(
                  dateNow.getYear(), dateNow.getMonthValue(), dateNow.getDayOfMonth()
            )};

            return String.join(";", dateArray);
        }

    }



}