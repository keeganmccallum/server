package com.torodb.poc.backend;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.Optional;

import org.junit.Before;
import org.junit.Test;

import com.torodb.core.TableRef;
import com.torodb.core.d2r.CollectionData;
import com.torodb.core.d2r.D2RTranslator;
import com.torodb.core.d2r.DocPartData;
import com.torodb.core.d2r.DocPartRow;
import com.torodb.core.transaction.metainf.FieldType;
import com.torodb.core.transaction.metainf.ImmutableMetaCollection;
import com.torodb.core.transaction.metainf.ImmutableMetaDatabase;
import com.torodb.core.transaction.metainf.ImmutableMetaSnapshot;
import com.torodb.core.transaction.metainf.MetaDocPart;
import com.torodb.core.transaction.metainf.MetaField;
import com.torodb.core.transaction.metainf.MetainfoRepository.SnapshotStage;
import com.torodb.core.transaction.metainf.MutableMetaCollection;
import com.torodb.core.transaction.metainf.MutableMetaSnapshot;
import com.torodb.kvdocument.conversion.json.JacksonJsonParser;
import com.torodb.kvdocument.conversion.json.JsonParser;
import com.torodb.kvdocument.types.NullType;
import com.torodb.kvdocument.values.KVDocument;
import com.torodb.kvdocument.values.KVValue;
import com.torodb.metainfo.cache.mvcc.MvccMetainfoRepository;

public class Document2RelTest {
	
	//TODO: Change to final implementation code
	private static final String ARRAY_VALUE_NAME="v";
	private static final boolean IS_ARRAY=true;
	private static final boolean IS_SUBDOCUMENT=false;
	private static final Integer NO_SEQ = null;

	private JsonParser parser = new JacksonJsonParser();

	private MutableMetaCollection mutableMetaCollection;

	private static final String DB1 = "test1";
	private static final String COLL1 = "coll1Test1";
	private static final String COLL2 = "coll2Test1";

	private static final String DB2 = "test2";
	private static final String COLL3 = "coll1Test2";
	private static final String COLL4 = "coll2Test2";

	private static ImmutableMetaSnapshot currentView = new ImmutableMetaSnapshot.Builder()
			.add(new ImmutableMetaDatabase.Builder(DB1, DB1)
					.add(new ImmutableMetaCollection.Builder(COLL1, COLL1).build())
					.add(new ImmutableMetaCollection.Builder(COLL2, COLL2).build()).build())
			.add(new ImmutableMetaDatabase.Builder(DB2, DB2)
					.add(new ImmutableMetaCollection.Builder(COLL3, COLL3).build())
					.add(new ImmutableMetaCollection.Builder(COLL4, COLL4).build()).build())
			.build();

	@Before
	public void setup() {
		MvccMetainfoRepository mvccMetainfoRepository = new MvccMetainfoRepository(currentView);

		MutableMetaSnapshot mutableSnapshot;
		try (SnapshotStage snapshot = mvccMetainfoRepository.startSnapshotStage()) {
			mutableSnapshot = snapshot.createMutableSnapshot();
		}

		mutableMetaCollection = mutableSnapshot.getMetaDatabaseByName(DB1).getMetaCollectionByName(COLL1);
	}

	@Test
	public void rootDocMapsToATableWithEmptyName() {
		CollectionData collectionData = parseDocument("docs/OneField.json");
		assertNotNull(findDocPart(collectionData, ""));
	}
	
	@Test
	public void aFieldMapsToAColumn() {
		CollectionData collectionData = parseDocument("docs/OneField.json");

		DocPartData rootDocPart = findRootDocPart(collectionData);
		DocPartRow firstRow = rootDocPart.iterator().next();
		
		int fieldPosition = findFieldPosition(rootDocPart, "name", FieldType.STRING);
		assertTrue(fieldPosition>=0);
		assertTrue(rootDocPart.iterator().hasNext());
		assertExistValueInPosition(firstRow, 0, "John");
	}
	
	@Test
	public void multipleFieldsMapsToMultipleColumns() {
		CollectionData collectionData = parseDocument("docs/MultipleFields.json");
		
		DocPartData rootDocPart = findRootDocPart(collectionData);
		DocPartRow firstRow = rootDocPart.iterator().next();
		
		int fieldName = findFieldPosition(rootDocPart, "name", FieldType.STRING);
		assertTrue(fieldName>=0);
		assertExistValueInPosition(firstRow, fieldName, "John");
		
		int fieldAge= findFieldPosition(rootDocPart, "age", FieldType.INTEGER);
		assertTrue(fieldAge>=0);
		assertExistValueInPosition(firstRow, fieldAge, 34);
	}
	
	@Test
	public void nullFieldMapsToNullColumn() {
		CollectionData collectionData = parseDocument("docs/NullField.json");
		
		DocPartData rootDocPart = findRootDocPart(collectionData);
		DocPartRow firstRow = rootDocPart.iterator().next();
		
		int fieldAge= findFieldPosition(rootDocPart, "age", FieldType.NULL);
		assertTrue(fieldAge>=0);
		assertExistValueInPosition(firstRow, fieldAge, null);
		
	}
	
	@Test
	public void emptyArrayMapsToChildColumn() {
		CollectionData collectionData = parseDocument("docs/EmptyArray.json");
		
		DocPartData rootDocPart = findRootDocPart(collectionData);
		DocPartRow firstRow = rootDocPart.iterator().next();
		
		int fieldDepartment= findFieldPosition(rootDocPart, "department", FieldType.CHILD);
		assertTrue(fieldDepartment>=0);
		assertExistValueInPosition(firstRow, fieldDepartment, true);
	}
	
	@Test
	public void arrayCreatesRowInParentTable() {
		CollectionData collectionData = parseDocument("docs/ArrayWithScalar.json");
		
		DocPartData rootDocPart = findRootDocPart(collectionData);
		DocPartRow firstRow = rootDocPart.iterator().next();
		
		int fieldMonths= findFieldPosition(rootDocPart, "months", FieldType.CHILD);
		assertTrue(fieldMonths>=0);
		assertExistValueInPosition(firstRow, fieldMonths, IS_ARRAY);
	}
	
	@Test
	public void arrayMapsToNewTable() {
		CollectionData collectionData = parseDocument("docs/ArrayWithScalar.json");
		assertNotNull(findDocPart(collectionData, "months"));
	}
	
	@Test
	public void scalarInArrayMapsToColumnWithValue() {
		CollectionData collectionData = parseDocument("docs/ArrayWithScalar.json");
		DocPartData monthsDocPart = findDocPart(collectionData, "months");
		DocPartRow firstRow = monthsDocPart.iterator().next();
		
		int fieldScalar= findFieldPosition(monthsDocPart, ARRAY_VALUE_NAME, FieldType.INTEGER);
		assertTrue(fieldScalar>=0);
		assertExistValueInPosition(firstRow, fieldScalar, 1);
	}
	
	@Test
	public void subDocumentCreatesRowInParentTable() {
		CollectionData collectionData = parseDocument("docs/SubDocument.json");
		
		DocPartData rootDocPart = findRootDocPart(collectionData);
		DocPartRow firstRow = rootDocPart.iterator().next();
		
		int fieldAddress= findFieldPosition(rootDocPart, "address", FieldType.CHILD);
		assertTrue(fieldAddress>=0);
		assertExistValueInPosition(firstRow, fieldAddress, IS_SUBDOCUMENT);
	}
	
	@Test
	public void subDocumentMapsToNewTable() {
		CollectionData collectionData = parseDocument("docs/SubDocument.json");
		assertNotNull(findDocPart(collectionData, "address"));
	}
	
	@Test
	public void subDocumentFiledsMapsIntoNewTable() {
		CollectionData collectionData = parseDocument("docs/SubDocument.json");
		
		DocPartData addressDocPart = findDocPart(collectionData, "address");
		DocPartRow firstRow = addressDocPart.iterator().next();
		
		int fieldStreet = findFieldPosition(addressDocPart, "street", FieldType.STRING);
		assertTrue(fieldStreet>=0);
		assertExistValueInPosition(firstRow, fieldStreet, "My Home");
		
		int fieldZip= findFieldPosition(addressDocPart, "zip", FieldType.INTEGER);
		assertTrue(fieldZip>=0);
		assertExistValueInPosition(firstRow, fieldZip, 28034);
	}
	
	@Test
	public void subDocumentInArrayMapsToNewTable() {
		CollectionData collectionData = parseDocument("docs/ArrayWithDocument.json");
		
		DocPartData departmentDocPart = findDocPart(collectionData, "department");
		DocPartRow firstRow = departmentDocPart.iterator().next();

		int fieldName = findFieldPosition(departmentDocPart, "name", FieldType.STRING);
		assertTrue(fieldName>=0);
		assertExistValueInPosition(firstRow, fieldName, "dept1");
	}
	
	@Test
	public void subDocumentHeterogeneousInArrayMapsToSameTable() {
		CollectionData collectionData = parseDocument("docs/ArrayWithHeteroDocument.json");
		
		DocPartData rootDocPart = findRootDocPart(collectionData);
		DocPartRow rootRow = rootDocPart.iterator().next();
		
		DocPartData departmentDocPart = findDocPart(collectionData, "department");

		DocPartRow firstRow = findRowSeq(departmentDocPart, rootRow.getRid(), 0);
		int fieldName = findFieldPosition(departmentDocPart, "name", FieldType.STRING);
		assertTrue(fieldName>=0);
		assertExistValueInPosition(firstRow, fieldName, "dept1");
		
		DocPartRow secondRow = findRowSeq(departmentDocPart, rootRow.getRid(), 1);
		int fieldCode = findFieldPosition(departmentDocPart, "code", FieldType.INTEGER);
		assertTrue(fieldCode>=0);
		assertExistValueInPosition(secondRow, fieldCode, 54);
	}
	
	@Test
	public void subDocumentAndArrayCanMapToSameTable() {
		CollectionData collectionData = parseDocument("docs/ArrayAndObjectCollision.json");

		DocPartData departmentsDocPart = findDocPart(collectionData, "departments");
		int fieldDept = findFieldPosition(departmentsDocPart, "dept", FieldType.CHILD);
		assertTrue(fieldDept >= 0);

		DocPartRow row4Document = findRowSeq(departmentsDocPart, 0);
		assertNotNull(row4Document);
		assertExistValueInPosition(row4Document, fieldDept, IS_SUBDOCUMENT);

		DocPartRow row4Array = findRowSeq(departmentsDocPart, 1);
		assertNotNull(row4Array);
		assertExistValueInPosition(row4Array, fieldDept, IS_ARRAY);

		DocPartData deptDocPart = findDocPart(collectionData, "departments.dept");
		int fieldName = findFieldPosition(deptDocPart, "name", FieldType.STRING);
		assertTrue(fieldName >= 0);

		DocPartRow rowDocument = findRowSeq(deptDocPart, row4Document.getRid(), NO_SEQ);
		assertNotNull(rowDocument);
		assertExistValueInPosition(rowDocument, fieldName, "dept1");

		DocPartRow firstRowArray = findRowSeq(deptDocPart, row4Array.getRid(), 0);
		assertNotNull(firstRowArray);
		assertExistValueInPosition(firstRowArray, fieldName, "dept2");

		DocPartRow secondRowArray = findRowSeq(deptDocPart, row4Array.getRid(), 1);
		assertNotNull(secondRowArray);
		assertExistValueInPosition(secondRowArray, fieldName, "dept3");
	}
	
	
	@Test
	public void arrayInArrayMapsToNewTable() {
		CollectionData collectionData = parseDocument("docs/MultiArray.json");
		
		DocPartData monthsDocPart = findDocPart(collectionData, "months");
		DocPartRow firstRow = monthsDocPart.iterator().next();
		int fieldArray= findFieldPosition(monthsDocPart, ARRAY_VALUE_NAME, FieldType.CHILD);
		assertTrue(fieldArray >= 0);
		assertExistValueInPosition(firstRow, fieldArray, IS_ARRAY);
		
		DocPartData subArrayDocPart = findDocPart(collectionData, "months.$2");
		assertNotNull(subArrayDocPart);

		DocPartRow firstRowSubArray = findRowSeq(subArrayDocPart, firstRow.getRid(), 0);
		assertNotNull(firstRowSubArray);
		int fieldSubArray= findFieldPosition(subArrayDocPart, ARRAY_VALUE_NAME, FieldType.INTEGER);
		assertTrue(fieldSubArray >= 0);
		assertExistValueInPosition(firstRowSubArray, fieldSubArray, 1);
	}
	

	
	private DocPartData findRootDocPart(CollectionData collectionData){
		return findDocPart(collectionData,"");
	}
	
	private DocPartData findDocPart(CollectionData collectionData, String path){
		ArrayList<String> pathList=new ArrayList<>(Arrays.asList(path.split("\\.")));
		Collections.reverse(pathList);
		pathList.add("");
		String name = pathList.get(0); 
		for(DocPartData docPartData :collectionData){
			MetaDocPart metaDocPart = docPartData.getMetaDocPart();
			if (name.equals(metaDocPart.getTableRef().getName())){
				if (isSamePath(pathList, metaDocPart.getTableRef())){
					return docPartData;
				}
			}
		}
		return null;
	}
	
	private boolean isSamePath(ArrayList<String> pathList, TableRef tableRef){
		int idx=0;
		Optional<TableRef> table = Optional.of(tableRef);
		while (table.isPresent()){
			if (!pathList.get(idx).equals(table.get().getName())){
				return false;
			}
			idx++;
			table=table.get().getParent();
		}
		return true;
		
	}
	
	private DocPartRow findRowSeq(DocPartData docPartData, Integer seq){
		for(DocPartRow row: docPartData){
			if (row.getSeq()==seq){
				return row;
			}
		}
		return null;
	}
	
	private DocPartRow findRowSeq(DocPartData docPartData, int parentId, Integer seq){
		for(DocPartRow row: docPartData){
			if (row.getPid()==parentId && row.getSeq()==seq){
				return row;
			}
		}
		return null;
	}
	
	private int findFieldPosition(DocPartData docPartData, String name, FieldType type) {
		int idx = 0;
		Iterator<? extends MetaField> iterator = docPartData.orderedMetaFieldIterator();
		while (iterator.hasNext()) {
			MetaField field = iterator.next();
			if (field.getName().equals(name) && field.getType()==type){
				return idx; 
			}
			idx++;
		}
		return -1;
	}
	
	private boolean assertExistValueInPosition(DocPartRow row,int order, Object value){
		KVValue<?> kv = null;
		Iterator<KVValue<?>> iterator = row.iterator();
		for (int i=0;i<=order;i++){
			kv = iterator.next();
		}
		if (kv.getType()==NullType.INSTANCE){
			assertEquals(value,null);
		}else{
			assertEquals(value,kv.getValue());
		}
		return true;
	}

	private CollectionData parseDocument(String docName) {
		D2RTranslator translator = new D2RTranslatorImpl(mutableMetaCollection);
		KVDocument document = parser.createFromResource(docName);
		translator.translate(document);
		return translator.getCollectionDataAccumulator();
	}

}
