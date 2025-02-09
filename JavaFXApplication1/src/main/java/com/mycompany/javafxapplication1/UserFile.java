package com.mycompany.javafxapplication1;

import javafx.beans.property.SimpleLongProperty;
import javafx.beans.property.SimpleStringProperty;

public class UserFile {
    private final SimpleLongProperty id;
    private final SimpleStringProperty filename;
    private final SimpleStringProperty owner;
    private final SimpleStringProperty path;

    public UserFile(Long id, String filename, String owner, String path) {
        this.id = new SimpleLongProperty(id);
        this.filename = new SimpleStringProperty(filename);
        this.owner = new SimpleStringProperty(owner);
        this.path = new SimpleStringProperty(path);
    }

    public Long getId() { return id.get(); }
    public String getFilename() { return filename.get(); }
    public String getOwner() { return owner.get(); }
    public String getPath() { return path.get(); }
}