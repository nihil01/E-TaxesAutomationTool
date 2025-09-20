package com.bizcon.taxesautomator.models;

import javafx.beans.property.*;


public class Record {

    private SimpleStringProperty voen;
    private SimpleStringProperty searchStatus;
    private SimpleStringProperty asanNomre;
    private SimpleStringProperty asanId;
    private SimpleBooleanProperty oxunmamis;
    private SimpleBooleanProperty makeReport;
    private SimpleStringProperty baslangicTarixi;
    private SimpleStringProperty bitmeTarixi;

    public Record(){
        this.voen = new SimpleStringProperty("");
        this.searchStatus = new SimpleStringProperty("");
        this.asanNomre = new SimpleStringProperty("");
        this.asanId = new SimpleStringProperty("");
        this.oxunmamis = new SimpleBooleanProperty(false);
        this.baslangicTarixi = new SimpleStringProperty("");
        this.bitmeTarixi = new SimpleStringProperty("");
        this.makeReport = new SimpleBooleanProperty(false);
    }

    public Record(String voen, String searchStatus, String asanNomre, String asanId, Boolean oxunmamis, Boolean makeReport,
                  String baslangicTarixi, String bitmeTarixi) {
        this.voen = new SimpleStringProperty(voen);
        this.searchStatus = new SimpleStringProperty(searchStatus);
        this.asanNomre = new SimpleStringProperty(asanNomre);
        this.asanId = new SimpleStringProperty(asanId);
        this.oxunmamis = new SimpleBooleanProperty(oxunmamis);
        this.baslangicTarixi = new SimpleStringProperty(baslangicTarixi);
        this.bitmeTarixi = new SimpleStringProperty(bitmeTarixi);
        this.makeReport = new SimpleBooleanProperty(makeReport);
    }

    public BooleanProperty getOxunmamis() {
        return oxunmamis;
    }

    public void setOxunmamis(Boolean oxunmamis) {
        this.oxunmamis.set(oxunmamis);
    }

    public void setMakeReport(Boolean makeReport) {
        this.makeReport.set(makeReport);
    }

    public BooleanProperty getMakeReport() {
        return makeReport;
    }

    // VOEN
    public String getVoen() {
        return voen.get();
    }

    public void setVoen(String voen) {
        this.voen.set(voen);
    }

    public StringProperty voenProperty() {
        return voen;
    }

    // Axtaris
    public String getSearchStatus() {
        return searchStatus.get();
    }

    public void setSearchStatus(String searchStatus) {
        this.searchStatus.set(searchStatus);
    }

    public StringProperty searchStatusProperty() {
        return searchStatus;
    }

    // Asan Nomre
    public String getAsanNomre() {
        return asanNomre.get();
    }

    public void setAsanNomre(String asanNomre) {
        this.asanNomre.set(asanNomre);
    }

    public StringProperty asanNomreProperty() {
        return asanNomre;
    }

    // Asan ID
    public String getAsanId() {
        return asanId.get();
    }

    public void setAsanId(String asanId) {
        this.asanId.set(asanId);
    }

    public StringProperty asanIdProperty() {
        return asanId;
    }

    // Baslangic tarixi
    public String getBaslangicTarixi() {
        return baslangicTarixi.get();
    }

    public void setBaslangicTarixi(String baslangicTarixi) {
        this.baslangicTarixi.set(baslangicTarixi);
    }

    public StringProperty baslangicTarixiProperty() {
        return baslangicTarixi;
    }

    // Bitme tarixi
    public String getBitmeTarixi() {
        return bitmeTarixi.get();
    }

    public void setBitmeTarixi(String bitmeTarixi) {
        this.bitmeTarixi.set(bitmeTarixi);
    }

    public StringProperty bitmeTarixiProperty() {
        return bitmeTarixi;
    }

    @Override
    public String toString() {
        return "Record{" +
                "voen=" + voen +
                ", searchStatus=" + searchStatus +
                ", asanNomre=" + asanNomre +
                ", asanId=" + asanId +
                ", oxunmamis=" + oxunmamis +
                ", baslangicTarixi=" + baslangicTarixi +
                ", bitmeTarixi=" + bitmeTarixi +
                '}';
    }
}
