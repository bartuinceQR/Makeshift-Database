package com.kosaki.storage;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class StorageSystem {
    private final int PAGE_SIZE = 1536;
    private final int PAGE_HEADER_SIZE = 20;
    private final int PAGE_RECORDS_MAX = 10;
    private final int RECORD_SIZE = 100;
    private final int RECORD_HEADER_SIZE = 30;
    private final int RECORD_FIELDNAME_MAX = 20;
    private final int RECORD_FIELDS_MAX = 10;
    private final int TYPE_NAME_MAX = 20;
    private final int TYPE_SIZE = 200;

    private int catalogHeaderOffset;

    private InputStreamReader isreader = new InputStreamReader(System.in);
    private BufferedReader input = new BufferedReader(isreader);
    private RandomAccessFile catalog;


    public class Record {
        int primaryKey;
        String typename;
        int fieldsUsed;
        boolean isFull;
        boolean isEmpty;
        ArrayList<Integer> fieldvalues;

        public int getPrimaryKey() {
            return primaryKey;
        }

        public void setPrimaryKey(int primaryKey) {
            this.primaryKey = primaryKey;
        }

        public String getTypename() {
            return typename;
        }

        void setTypename(String typename) {
            this.typename = typename;
        }

        public int getFieldsUsed() {
            return fieldsUsed;
        }

        void setFieldsUsed(int fieldsUsed) {
            this.fieldsUsed = fieldsUsed;
        }

        public boolean isFull() {
            return isFull;
        }

        void setFull(boolean full) {
            isFull = full;
        }

        public boolean isEmpty() {
            return isEmpty;
        }

        void setEmpty(boolean empty) {
            isEmpty = empty;
        }

        public List<Integer> getFieldvalues() {
            return fieldvalues;
        }

        Record(int primaryKey){
            this.primaryKey = primaryKey;
            fieldvalues = new ArrayList();
        }

        public String toString() {
            String res = "";
            res += "[Primary Key: " + primaryKey + ",\n";
            res += "Type Name: " + typename + ",\n";
            res += "Field Count: " + fieldsUsed + ",\n";
            res += "Is Full: " + isFull + ",\n";
            res += "Is Empty: " + isEmpty + ",\n";
            res += "Field Values: " + Arrays.toString(fieldvalues.toArray()) + "]\n";
            return res;
        }
    }

    StorageSystem(String filename) throws IOException{

        boolean filefound = true;

        //what the fuck?
        //this is SO FUCKING DUMB
        //IF A FILE CAN'T BE FOUND JUST CREATE IT
        //THE DOCS LITERALLY SAY RANDOMACCESSFILES CAN CREATE FILES, FUCK OFF
        File f = new File("./catalogfiles/");
        if (!f.exists()){
            f.mkdir();
            filefound = false;
        }
        //if I make it a .txt it's easily readable, and also very ugly
        f = new File("./catalogfiles/syscat.cat");
        if (!f.exists()){
            f.createNewFile();
            filefound = false;
        }

        catalog = new RandomAccessFile(f, "rw");



        catalogHeaderOffset = 44;

        if (!filefound) {
            catalog.seek(0);
            catalog.writeInt(PAGE_SIZE);
            catalog.writeInt(PAGE_HEADER_SIZE);
            catalog.writeInt(PAGE_RECORDS_MAX);
            catalog.writeInt(RECORD_SIZE);
            catalog.writeInt(RECORD_HEADER_SIZE);
            catalog.writeInt(RECORD_FIELDNAME_MAX);
            catalog.writeInt(RECORD_FIELDS_MAX);
            catalog.writeInt(TYPE_NAME_MAX);
            catalog.writeInt(TYPE_SIZE);
            catalog.writeInt(1); // current number of pages
            catalog.writeInt(0); //current number of types
            initialisePageCatalog(1);
        }

    }

    public void createType() throws IOException {
        String typename = getInputString("Please enter a typename you want to create. Max length is 20 chars.", 20);

        catalog.seek(36); //the part that holds the number of pages
        int totalpages = catalog.readInt();
        int totaltypes = catalog.readInt();

        catalog.seek(catalogHeaderOffset);

        long entryposition = 0; //literally just saves me from writing the same code twice, REEEEEEEEEEEEEE
        int page = -1;
        boolean found = false;

        outerloop: //we want to break out of this one
        for (int pg = 1; pg <= totalpages ; pg++) {
            long pagelocation = catalogHeaderOffset + (PAGE_SIZE + PAGE_HEADER_SIZE) * (pg - 1); //start of page i
            catalog.seek(pagelocation);

            int typecount = catalog.readInt();
            catalog.skipBytes(4); //nobody cares about the page number
            boolean pagefull = catalog.readBoolean();
            boolean pageempty = catalog.readBoolean();

            catalog.seek(pagelocation + PAGE_HEADER_SIZE); //skip the rest of the page's header
            long reclocation = catalog.getFilePointer();

            for (int i = 0; i < PAGE_RECORDS_MAX; i++) {
                catalog.seek(reclocation + i * TYPE_SIZE);
                String existingname = "";

                for (int j = 0; j < TYPE_NAME_MAX; j++) {
                    existingname += (char)catalog.readByte(); // type name
                }
                existingname = existingname.replaceAll("\\s+",""); //hacky way to see if the field is empty or not

                if (existingname.equals("") && !found){ //that type slot is empty
                    entryposition = reclocation + i * TYPE_SIZE;
                    page = pg-1;
                    found = true;
                }

                if (typename.equals(existingname)){
                    System.out.println("Typename already exists!");
                    return;
                }
            }
        }
        if (!found){
            page = totalpages;
            initialisePageCatalog(page+1);
            entryposition = catalogHeaderOffset + (PAGE_SIZE + PAGE_HEADER_SIZE) * (totalpages) + PAGE_HEADER_SIZE;
            catalog.seek(36); //the part that holds the number of pages
            catalog.writeInt(totalpages + 1); //increase page count by one
        }

        catalog.seek(catalogHeaderOffset + (PAGE_SIZE + PAGE_HEADER_SIZE) * page);
        int recs = catalog.readInt();
        catalog.skipBytes(4); //skip page number and ispagefull
        if (recs + 1 == PAGE_RECORDS_MAX){
            catalog.writeBoolean(true);
        }else{
            catalog.skipBytes(1);
        }
        catalog.writeBoolean(false); //ispaageempty = false
        catalog.seek(catalogHeaderOffset + (PAGE_SIZE + PAGE_HEADER_SIZE) * page);
        catalog.writeInt(recs+1); //increase total type count by 1

        catalog.seek(40); //holds number of records
        catalog.writeInt(totaltypes + 1); //increase types by one

        catalog.seek(entryposition);

        catalog.writeBytes(typename);
        for (int i = typename.length() ; i < TYPE_NAME_MAX ; i++){
            catalog.writeByte(' ');
        }

        String fieldcount = getInputString("Please enter the amount of fields. Max is " + RECORD_FIELDS_MAX + ".", 2);
        int fieldtotal = Integer.parseInt(fieldcount);
        catalog.writeInt(1); //page count
        catalog.writeInt(0); //record count
        catalog.writeInt(fieldtotal); //field count
        catalog.writeInt(0); //key count

        for (int i = 0 ; i < fieldtotal ; i++){
            String fieldname = getInputString("Please enter field name " + (i+1) + ". Max length is " + RECORD_FIELDNAME_MAX + ".", RECORD_FIELDNAME_MAX);
            catalog.writeBytes(fieldname);
            catalog.skipBytes(RECORD_FIELDNAME_MAX - fieldname.length());
        }

        createTypefile(typename);
    }

    public void deleteType() throws IOException{
        String typename = getInputString("Please enter a typename you want to delete. Max length is 20 chars.", 20);

        catalog.seek(36); //the part that holds the number of pages
        int totalpages = catalog.readInt();
        int totaltypes = catalog.readInt();

        catalog.seek(catalogHeaderOffset);

        long entryposition = 0; //literally just saves me from writing the same code twice, REEEEEEEEEEEEEE
        int page = -1;
        boolean found = false;

        outerloop: //we want to break out of this one
        for (int pg = 1; pg <= totalpages ; pg++) {
            long pagelocation = catalogHeaderOffset + (PAGE_SIZE + PAGE_HEADER_SIZE) * (pg - 1); //start of page i
            catalog.seek(pagelocation);

            int recordcount = catalog.readInt();
            catalog.skipBytes(4); //nobody cares about the page number
            boolean pagefull = catalog.readBoolean();
            boolean pageempty = catalog.readBoolean();

            if (!pageempty) {
                catalog.seek(pagelocation + PAGE_HEADER_SIZE); //skip the rest of the page's header
                long reclocation = catalog.getFilePointer();

                for (int i = 0; i < PAGE_RECORDS_MAX; i++) {
                    catalog.seek(reclocation + i * TYPE_SIZE);
                    String existingname = "";

                    for (int j = 0; j < TYPE_NAME_MAX; j++) {
                        existingname += (char) catalog.readByte(); // type name
                    }
                    existingname = existingname.replaceAll("\\s+", ""); //hacky way to see if the field is empty or not

                    if (existingname.equals(typename)) { //that type slot is empty
                        entryposition = reclocation + i * TYPE_SIZE;
                        page = pg - 1;
                        found = true;
                        break outerloop;
                    }
                }
            }
        }

        if (!found){
            System.out.println("Type not found. Sorry!");
            return;
        }

        catalog.seek(entryposition);
        for (int j = 0; j < TYPE_NAME_MAX; j++) {
            catalog.writeByte(' ');
        }

        catalog.writeInt(0); //page count
        catalog.writeInt(0); //record count
        catalog.writeInt(0); //field count
        catalog.writeInt(0); //key count

        //empty all fields
        //....I really shoulda written a function for this.
        for (int j = 0; j < RECORD_FIELDS_MAX ; j++){
            for (int k = 0; k < RECORD_FIELDNAME_MAX ; k++){
                catalog.writeByte(' ');
            }
        }

        catalog.seek(catalogHeaderOffset + (PAGE_HEADER_SIZE + PAGE_SIZE) * page);
        int recs = catalog.readInt();
        catalog.skipBytes(4);
        if (recs == PAGE_RECORDS_MAX){
            catalog.writeBoolean(false); //not full anymore
        } else if(recs == 1){
            catalog.skipBytes(1);
            catalog.writeBoolean(true); //empty now
        }

        catalog.seek(catalogHeaderOffset + (PAGE_HEADER_SIZE + PAGE_SIZE) * page);
        catalog.writeInt(recs - 1);
        catalog.seek(40); //decrease from global counter as well
        catalog.writeInt(totaltypes - 1);

        Files.deleteIfExists(Paths.get("./databasefiles/" + typename + ".dat"));

    }

    public void listAllTypes() throws IOException{
        ArrayList<String> namelist = new ArrayList();

        catalog.seek(36); //the part that holds the number of pages
        int totalpages = catalog.readInt();

        catalog.seek(catalogHeaderOffset);

        outerloop: //we want to break out of this one
        for (int pg = 1; pg <= totalpages ; pg++) {
            long pagelocation = catalogHeaderOffset + (PAGE_SIZE + PAGE_HEADER_SIZE) * (pg - 1); //start of page i

            catalog.seek(pagelocation + PAGE_HEADER_SIZE); //skip the rest of the page's header
            long reclocation = catalog.getFilePointer();

            for (int i = 0; i < PAGE_RECORDS_MAX; i++) {
                catalog.seek(reclocation + i * TYPE_SIZE);
                String existingname = "";

                for (int j = 0; j < TYPE_NAME_MAX; j++) {
                    existingname += (char) catalog.readByte(); // type name
                }
                existingname = existingname.replaceAll("\\s+", ""); //hacky way to see if the field is empty or not

                if (!(existingname.equals("") || namelist.contains(existingname))) { //that type slot is empty and we didnt already add it (failsafe)
                    namelist.add(existingname);
                }
            }
        }
        System.out.println(Arrays.toString(namelist.toArray()));
    }

    public void createRecord() throws IOException {
        String typename = getInputString("Please enter the typename of the record you want to add. Max length is 20 chars.", 20);

        catalog.seek(36); //the part that holds the number of pages
        int totalpages = catalog.readInt();

        long entryposition = 0; //literally just saves me from writing the same code twice, REEEEEEEEEEEEEE
        boolean found = false;

        outerloop: //we want to break out of this one
        for (int pg = 1; pg <= totalpages ; pg++) {
            long pagelocation = catalogHeaderOffset + (PAGE_SIZE + PAGE_HEADER_SIZE) * (pg - 1); //start of page i
            catalog.seek(pagelocation);
            catalog.skipBytes(4); //nobody cares about the page number

            catalog.seek(pagelocation + PAGE_HEADER_SIZE); //skip the rest of the page's header
            long reclocation = catalog.getFilePointer();

            for (int i = 0; i < PAGE_RECORDS_MAX; i++) {
                catalog.seek(reclocation + i * TYPE_SIZE);
                StringBuilder existingname = new StringBuilder();

                for (int j = 0; j < TYPE_NAME_MAX; j++) {
                    existingname.append((char) catalog.readByte()); // type name
                }
                existingname = new StringBuilder(existingname.toString().replaceAll("\\s+", "")); //hacky way to see if the field is empty or not

                if (existingname.toString().equals(typename)) { //that type slot is empty
                    entryposition = reclocation + i * TYPE_SIZE;
                    found = true;
                    break outerloop;
                }
            }
        }

        if (!found){
            System.out.println("Type not found. Sorry!");
            return;
        }

        catalog.seek(entryposition);
        catalog.skipBytes(TYPE_NAME_MAX);
        int typepages = catalog.readInt();
        int typerecord = catalog.readInt();
        int typefields = catalog.readInt();
        int typekeys = catalog.readInt();

        ArrayList<String> fieldnames = new ArrayList<>();

        for (int i = 0; i < typefields ; i++){
            StringBuilder fname = new StringBuilder();
            for (int j = 0; j < RECORD_FIELDNAME_MAX; j++){
                fname.append((char) catalog.readByte());
            }
            fname = new StringBuilder(fname.toString().replaceAll("\\s+", "")); //hacky way to see if the field is empty or not
            fieldnames.add(fname.toString());
        }

        System.out.println(Arrays.toString(fieldnames.toArray()));

        File f = new File ("./databasefiles/" + typename + ".dat");
        RandomAccessFile typefile = new RandomAccessFile(f, "rw");

        typefile.seek(0);

        System.out.println(typefile.length());

        boolean foundPage = false;

        recordloop:
        for (int pg = 1; pg <= typepages ; pg++) {
            long pagelocation = (PAGE_SIZE + PAGE_HEADER_SIZE) * (pg - 1); //start of page i
            typefile.seek(pagelocation);

            int currentRecs = typefile.readInt();
            int pagenum = typefile.readInt();
            boolean pagefull = typefile.readBoolean();
            boolean pageempty = typefile.readBoolean();

            if (pagefull){
                continue;
            }

            typefile.seek(pagelocation + PAGE_HEADER_SIZE); //skip the rest of the page's header
            long recLocation = pagelocation + PAGE_HEADER_SIZE;

            for (int i = 0; i < PAGE_RECORDS_MAX; i++) {
                long recordPos = recLocation + i * (RECORD_HEADER_SIZE + RECORD_SIZE);

                typefile.seek(recordPos);
                //header part
                typefile.skipBytes(4); //fuck the primary key, it's empty right now
                typefile.skipBytes(RECORD_FIELDNAME_MAX); //fuck the typename, that's irrelevant
                typefile.skipBytes(4); //fuck the fields used too, this'll be filled later, just like the typename

                boolean isFull = typefile.readBoolean();
                boolean isEmpty = typefile.readBoolean(); //..these two are the same thing

                if (!isFull){

                    foundPage = true;

                    typefile.seek(recordPos);
                    typefile.writeInt(typekeys+1); //key number of record
                    typefile.writeBytes(typename);
                    for (int j = typename.length() ; j < TYPE_NAME_MAX ; j++){
                        typefile.writeByte(' ');
                    }
                    typefile.writeInt(typefields);
                    typefile.writeBoolean(true);
                    typefile.writeBoolean(false);

                    typefile.seek(recordPos + RECORD_HEADER_SIZE);

                    for (int ff = 0 ; ff < typefields ; ff++){
                        String fieldvalue = getInputString("Please enter value for " + fieldnames.get(ff) + ". Integers only.", 10);
                        int fieldvalueInt = Integer.parseInt(fieldvalue);
                        typefile.writeInt(fieldvalueInt);
                    }

                    typefile.seek(pagelocation);

                    typefile.writeInt(currentRecs + 1); //increase record count by 1
                    typefile.skipBytes(4);
                    if (currentRecs + 1 == PAGE_RECORDS_MAX){
                        typefile.writeBoolean(true); //page is now full
                    }else{
                        typefile.skipBytes(4);
                    }

                    typefile.writeBoolean(false); //page is not empty, ever

                    break recordloop;
                }
            }
        }

        if (!foundPage) {
            initialisePage(typefile, totalpages+1);

            long recordPos = (PAGE_SIZE + PAGE_HEADER_SIZE) * totalpages;

            typefile.seek(recordPos + PAGE_HEADER_SIZE); //start of page i, no header

            typefile.writeBytes(typename);
            for (int j = typename.length() ; j < TYPE_NAME_MAX ; j++){
                typefile.writeByte(' ');
            }
            typefile.writeInt(typefields);
            typefile.writeBoolean(true);
            typefile.writeBoolean(false);

            typefile.seek(recordPos + RECORD_HEADER_SIZE);

            for (int ff = 0 ; ff < typefields ; ff++){
                String fieldvalue = getInputString("Please enter value for " + fieldnames.get(ff) + ". Integers only.", 10);
                int fieldvalueInt = Integer.parseInt(fieldvalue);
                typefile.writeInt(fieldvalueInt);
            }

            typefile.seek(recordPos);

            typefile.writeInt(1); //increase record count by 1
            typefile.skipBytes(4); //skip current page number
            typefile.skipBytes(4); //page is never full
            typefile.writeBoolean(false); //page is not empty, ever

            catalog.seek(entryposition);
            catalog.skipBytes(TYPE_NAME_MAX);
            catalog.writeInt(typepages+1); //increase total pages by 1

        }

        catalog.seek(entryposition);
        catalog.skipBytes(TYPE_NAME_MAX);
        catalog.skipBytes(4); //pages were already changed in the "new page" case
        catalog.writeInt(typerecord + 1); //increase total records by 1
        catalog.skipBytes(4); //fields used does not change
        catalog.writeInt(typekeys + 1); //increase primary key by 1
    }

    public void deleteRecord() throws IOException {
        String typename = getInputString("Enter the typename of the record you want to delete. Max length is 20 chars.", 20);

        catalog.seek(36); //the part that holds the number of pages
        int totalpages = catalog.readInt();

        catalog.seek(catalogHeaderOffset);

        long entryposition = 0; //literally just saves me from writing the same code twice, REEEEEEEEEEEEEE
        boolean found = false;

        outerloop: //we want to break out of this one
        for (int pg = 1; pg <= totalpages ; pg++) {
            long pagelocation = catalogHeaderOffset + (PAGE_SIZE + PAGE_HEADER_SIZE) * (pg - 1); //start of page i
            catalog.seek(pagelocation);
            catalog.skipBytes(4); //nobody cares about the page number

            catalog.seek(pagelocation + PAGE_HEADER_SIZE); //skip the rest of the page's header
            long reclocation = catalog.getFilePointer();

            for (int i = 0; i < PAGE_RECORDS_MAX; i++) {
                catalog.seek(reclocation + i * TYPE_SIZE);
                String existingname = "";

                for (int j = 0; j < TYPE_NAME_MAX; j++) {
                    existingname += (char) catalog.readByte(); // type name
                }
                existingname = existingname.replaceAll("\\s+", ""); //hacky way to see if the field is empty or not

                if (existingname.equals(typename)) { //that type slot is empty
                    entryposition = reclocation + i * TYPE_SIZE;
                    found = true;
                    break outerloop;
                }
            }
        }

        if (!found){
            System.out.println("Type not found. Sorry!");
            return;
        }

        catalog.seek(entryposition);
        catalog.skipBytes(TYPE_NAME_MAX);
        int typepages = catalog.readInt();
        int typerecord = catalog.readInt();
        int typefields = catalog.readInt();
        int typekeys = catalog.readInt();

        String primarykey = getInputString("Enter the primary key of the record you want to delete! Do it, dummy!", 10);
        int keyval = Integer.parseInt(primarykey);
        while (keyval > typekeys){
            primarykey = getInputString("There aren't even that many keys, what are you DOING? Just type a negative number to quit if you're so inclined, or try again!", 10);
            keyval = Integer.parseInt(primarykey);
        }

        if (keyval <= 0){
            System.out.println("Meh, whatever.");
            return;
        }

        File f = new File ("/databasefiles/" + typename + ".dat");
        RandomAccessFile typefile = new RandomAccessFile(f, "rw");

        typefile.seek(0);

        boolean foundRecord = false;

        for (int pg = 1; pg <= typepages ; pg++) {
            long pagelocation = (PAGE_SIZE + PAGE_HEADER_SIZE) * (pg - 1); //start of page i
            typefile.seek(pagelocation);

            int currentRecs = typefile.readInt();
            int pagenum = typefile.readInt();
            boolean pagefull = typefile.readBoolean();
            boolean pageempty = typefile.readBoolean();

            if (pageempty){
                continue;
            }

            typefile.seek(pagelocation + PAGE_HEADER_SIZE); //skip the rest of the page's header
            long reclocation = pagelocation + PAGE_HEADER_SIZE;

            for (int i = 0; i < PAGE_RECORDS_MAX; i++) {
                long recordPos = reclocation + i * (RECORD_HEADER_SIZE + RECORD_SIZE);

                typefile.seek(recordPos);
                //header part
                int writtenkey = typefile.readInt(); //fuck the primary key, it's empty right now
                typefile.skipBytes(20); //fuck the typename, that's irrelevant
                typefile.skipBytes(4); //fuck the fields used too, this'll be filled later, just like the typename

                boolean isFull = typefile.readBoolean();
                boolean isEmpty = typefile.readBoolean(); //..these two are the same thing

                if (isFull && (keyval == writtenkey)){

                    foundRecord = true;

                    typefile.seek(recordPos);
                    typefile.writeInt(-1); //BEGONE, KEY
                    for (int j = 0 ; j < TYPE_NAME_MAX ; j++){
                        typefile.writeByte(' ');
                    }
                    typefile.writeInt(typefields); //honestly why even edit this?
                    typefile.writeBoolean(false);
                    typefile.writeBoolean(true);

                    typefile.seek(recordPos + RECORD_HEADER_SIZE);

                    for (int ff = 0 ; ff < typefields ; ff++){
                        typefile.writeInt(0);
                    }

                    typefile.seek(pagelocation);

                    typefile.writeInt(currentRecs - 1); //increase record count by 1
                    typefile.skipBytes(4);
                    typefile.writeBoolean(false); //never full
                    if (currentRecs - 1 == 0){
                        typefile.writeBoolean(true); //page is now empty
                    }else{
                        typefile.skipBytes(4);
                    }

                }
            }
        }

        if (!foundRecord) {
            System.out.println("No record with such key. Too bad.");
            return;
        }

        catalog.seek(entryposition);
        catalog.skipBytes(TYPE_NAME_MAX);
        catalog.skipBytes(4); //pages were already changed in the "new page" case
        catalog.writeInt(typerecord - 1); //increase total records by 1
        //key number does NOT change
    }

    public void findRecordByKey() throws IOException {
        String typename = getInputString("Please enter the typename of the record you want to find... and keep it under 20 chars if you can.", 20);

        catalog.seek(36); //the part that holds the number of pages
        int totalpages = catalog.readInt();

        catalog.seek(catalogHeaderOffset);

        long entryposition = 0; //literally just saves me from writing the same code twice, REEEEEEEEEEEEEE
        boolean found = false;

        outerloop: //we want to break out of this one
        for (int pg = 1; pg <= totalpages ; pg++) {
            long pagelocation = catalogHeaderOffset + (PAGE_SIZE + PAGE_HEADER_SIZE) * (pg - 1); //start of page i
            catalog.seek(pagelocation);
            catalog.skipBytes(4); //nobody cares about the page number

            catalog.seek(pagelocation + PAGE_HEADER_SIZE); //skip the rest of the page's header
            long reclocation = catalog.getFilePointer();

            for (int i = 0; i < PAGE_RECORDS_MAX; i++) {
                catalog.seek(reclocation + i * TYPE_SIZE);
                String existingname = "";

                for (int j = 0; j < TYPE_NAME_MAX; j++) {
                    existingname += (char) catalog.readByte(); // type name
                }
                existingname = existingname.replaceAll("\\s+", ""); //hacky way to see if the field is empty or not

                if (existingname.equals(typename)) { //that type slot is empty
                    entryposition = reclocation + i * TYPE_SIZE;
                    found = true;
                    break outerloop;
                }
            }
        }

        if (!found){
            System.out.println("I'm sorry...there's no such type...");
            return;
        }

        catalog.seek(entryposition);
        catalog.skipBytes(TYPE_NAME_MAX);
        int typepages = catalog.readInt();
        int typerecord = catalog.readInt();
        int typefields = catalog.readInt();
        int typekeys = catalog.readInt();

        String primarykey = getInputString("Please enter the primary key of the record you want to find...", 10);
        int keyval = Integer.parseInt(primarykey);
        while (keyval > typekeys){
            primarykey = getInputString("There aren't that many keys...you can try again, or type a negative number to quit.", 10);
            keyval = Integer.parseInt(primarykey);
        }

        if (keyval <= 0){
            System.out.println("I see...see you then.");
            return;
        }

        File f = new File ("./databasefiles/" + typename + ".dat");
        RandomAccessFile typefile = new RandomAccessFile(f, "rw");

        typefile.seek(0);

        boolean foundRecord = false;

        recordloop:
        for (int pg = 1; pg <= typepages ; pg++) {
            long pagelocation = (PAGE_SIZE + PAGE_HEADER_SIZE) * (pg - 1); //start of page i
            typefile.seek(pagelocation);

            int currentRecs = typefile.readInt();
            int pagenum = typefile.readInt();
            boolean pagefull = typefile.readBoolean();
            boolean pageempty = typefile.readBoolean();

            if (pageempty){
                continue;
            }

            typefile.seek(pagelocation + PAGE_HEADER_SIZE); //skip the rest of the page's header
            long reclocation = pagelocation + PAGE_HEADER_SIZE;

            for (int i = 0; i < PAGE_RECORDS_MAX; i++) {
                long recordPos = reclocation + i * (RECORD_HEADER_SIZE + RECORD_SIZE);

                typefile.seek(recordPos);
                //header part
                int writtenkey = typefile.readInt(); //fuck the primary key, it's empty right now
                typefile.skipBytes(20); //fuck the typename, that's irrelevant
                typefile.skipBytes(4); //fuck the fields used too, this'll be filled later, just like the typename

                boolean isFull = typefile.readBoolean();
                boolean isEmpty = typefile.readBoolean(); //..these two are the same thing

                if (isFull && (keyval == writtenkey)){

                    foundRecord = true;

                    Record rec = new Record(keyval);
                    rec.setTypename(typename);
                    rec.setFieldsUsed(typefields);
                    rec.setFull(isFull);
                    rec.setEmpty(isEmpty);

                    typefile.seek(recordPos + RECORD_HEADER_SIZE);

                    for (int ff = 0 ; ff < typefields ; ff++){
                        int recordfield = typefile.readInt();
                        rec.fieldvalues.add(recordfield);
                    }

                    System.out.println(rec.toString());

                    break recordloop;
                }
            }
        }

        if (!foundRecord) {
            System.out.println("No record with such key. Too bad.");
        }
    }

    public void listRecords() throws IOException {
        String typename = getInputString("Enter the type name that you want to see listed, and be hasty please. Oh, and keep it under 20 chars.", 20);

        catalog.seek(36); //the part that holds the number of pages
        int totalpages = catalog.readInt();

        catalog.seek(catalogHeaderOffset);

        long entryposition = 0; //literally just saves me from writing the same code twice, REEEEEEEEEEEEEE
        boolean found = false;

        outerloop: //we want to break out of this one
        for (int pg = 1; pg <= totalpages ; pg++) {
            long pagelocation = catalogHeaderOffset + (PAGE_SIZE + PAGE_HEADER_SIZE) * (pg - 1); //start of page i
            catalog.seek(pagelocation);
            catalog.skipBytes(4); //nobody cares about the page number

            catalog.seek(pagelocation + PAGE_HEADER_SIZE); //skip the rest of the page's header
            long reclocation = catalog.getFilePointer();

            for (int i = 0; i < PAGE_RECORDS_MAX; i++) {
                catalog.seek(reclocation + i * TYPE_SIZE);
                StringBuilder existingname = new StringBuilder();

                for (int j = 0; j < TYPE_NAME_MAX; j++) {
                    existingname.append((char) catalog.readByte()); // type name
                }
                existingname = new StringBuilder(existingname.toString().replaceAll("\\s+", "")); //hacky way to see if the field is empty or not

                if (existingname.toString().equals(typename)) { //that type slot is empty
                    entryposition = reclocation + i * TYPE_SIZE;
                    found = true;
                    break outerloop;
                }
            }
        }

        if (!found){
            System.out.println("There's no such type. Oh well.");
            return;
        }

        catalog.seek(entryposition);
        catalog.skipBytes(TYPE_NAME_MAX);
        int typepages = catalog.readInt();
        int typerecord = catalog.readInt();
        int typefields = catalog.readInt();
        int typekeys = catalog.readInt();


        File f = new File ("./databasefiles/" + typename + ".dat");
        RandomAccessFile typefile = new RandomAccessFile(f, "rw");

        typefile.seek(0);

        boolean foundRecord = false;

        for (int pg = 1; pg <= typepages ; pg++) {
            long pagelocation = (PAGE_SIZE + PAGE_HEADER_SIZE) * (pg - 1); //start of page i
            typefile.seek(pagelocation);

            int currentRecs = typefile.readInt();
            int pagenum = typefile.readInt();
            boolean pagefull = typefile.readBoolean();
            boolean pageempty = typefile.readBoolean();

            /*if (pageempty){
                continue;
            }*/

            typefile.seek(pagelocation + PAGE_HEADER_SIZE); //skip the rest of the page's header
            long reclocation = pagelocation + PAGE_HEADER_SIZE;

            for (int i = 0; i < PAGE_RECORDS_MAX; i++) {
                long recordPos = reclocation + i * (RECORD_HEADER_SIZE + RECORD_SIZE);

                typefile.seek(recordPos);
                //header part
                int writtenkey = typefile.readInt(); //don't fuck the primary key actually
                typefile.skipBytes(20); //typename
                typefile.skipBytes(4); //fields used

                boolean isFull = typefile.readBoolean();
                boolean isEmpty = typefile.readBoolean(); //..these two are the same thing

                if (isFull){

                    System.out.println("Got one! *draws cards internally*");

                    Record rec = new Record(writtenkey);
                    rec.setTypename(typename);
                    rec.setFieldsUsed(typefields);
                    rec.setFull(isFull);
                    rec.setEmpty(isEmpty);

                    typefile.seek(recordPos + RECORD_HEADER_SIZE);

                    for (int ff = 0 ; ff < typefields ; ff++){
                        int recordfield = typefile.readInt();
                        rec.fieldvalues.add(recordfield);
                    }

                    System.out.println(rec.toString());
                }
            }
        }
    }


    private void createTypefile(String filename) throws IOException {
        File f = new File("./databasefiles/");
        if (!f.exists()){
            f.mkdir();
        }
        //if I make it a .txt it's easily readable, and also very ugly
        f = new File("./databasefiles/" + filename + ".dat");
        if (f.exists()){
            System.out.println("Typename already exists!");
            return;
        }

        RandomAccessFile typefile = new RandomAccessFile(f, "rw");
        initialisePage(typefile,1);


    }

    private void initialisePage(RandomAccessFile typefile, int pagenum) throws IOException {
        long pagelocation = (PAGE_SIZE + PAGE_HEADER_SIZE) * (pagenum - 1);
        typefile.seek(pagelocation);
        typefile.writeInt(0); //current number of records on page
        typefile.writeInt(pagenum); //current page number
        typefile.writeBoolean(false); //is page full
        typefile.writeBoolean(true); //is page empty
        typefile.seek(pagelocation + PAGE_HEADER_SIZE); //skip ahead of the page header
        long fp = typefile.getFilePointer();
        for (int i = 0; i < PAGE_RECORDS_MAX; i++){

            long recordPos = fp + i * (RECORD_HEADER_SIZE + RECORD_SIZE);

            typefile.seek(recordPos);
            typefile.writeInt(0); // primary key
            //header part
            for (int j = 0; j < RECORD_FIELDNAME_MAX; j++){
                typefile.writeByte(' '); // type name
            }
            typefile.writeInt(0); //number of fields used
            typefile.writeBoolean(false); //is record full
            typefile.writeBoolean(true); //is record empty

            typefile.seek(recordPos + RECORD_HEADER_SIZE); //rest of the bytes are filler, just go to the actual record

            //main part
            for (int j = 0; j < RECORD_FIELDS_MAX ; j++){
                typefile.writeInt(0);
            }

            //the rest of the allocated space is nonexistent bullshit
        }
    }

    private void initialisePageCatalog(int pagenum) throws IOException {
        long pagelocation = catalogHeaderOffset + (PAGE_SIZE + PAGE_HEADER_SIZE) * (pagenum - 1);
        catalog.seek(pagelocation); //head to the page
        catalog.writeInt(0); //current number of types on page
        catalog.writeInt(pagenum); //current page number
        catalog.writeBoolean(false); //is page full
        catalog.writeBoolean(true); //is page empty
        catalog.seek(pagelocation + PAGE_HEADER_SIZE); //skip the rest of the page's header
        long fp = catalog.getFilePointer();
        for (int i = 0; i < PAGE_RECORDS_MAX; i++){
            long typePos = fp + i * (TYPE_SIZE);
            catalog.seek(typePos);

            for (int j = 0; j < TYPE_NAME_MAX; j++){
                catalog.writeByte(' '); // type name
            }
            catalog.writeInt(0); //page count
            catalog.writeInt(0); //record count
            catalog.writeInt(0); //field count
            catalog.writeInt(0); //key count

            //main part
            for (int j = 0; j < RECORD_FIELDS_MAX ; j++){
                for (int k = 0; k < RECORD_FIELDNAME_MAX ; k++){
                    catalog.writeByte(' ');
                }
            }
        }
    }

    private String getInputString(String message, int maxlen) throws IOException {
        System.out.println(message);
        String ret = input.readLine();
        while (ret.length() > maxlen || ret.length() == 0){
            System.out.println(message);
            ret = input.readLine();
        }
        return ret;
    }

}
