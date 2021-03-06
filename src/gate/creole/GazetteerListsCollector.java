package gate.creole;

import java.io.*;
import java.util.*;

import gate.Annotation;
import gate.AnnotationSet;
import gate.creole.gazetteer.*;
import gate.util.*;

public class GazetteerListsCollector extends AbstractLanguageAnalyser {
  private static String PERSON_ANNOT_NAME = "PER";

  public void execute() throws gate.creole.ExecutionException {
    //reinitialise the stats
    statsPerType = new HashMap();

    //check the input
    if(document == null) {
      throw new ExecutionException(
        "No document to process!"
      );
    }

    if (gazetteer == null) {
      throw new ExecutionException(
        "No gazetteer set!"
      );
    }

    //if no annotation types given, then exit
    if ((this.annotationTypes == null) || annotationTypes.isEmpty()) {
      Out.prln("Gazetteer Lists Collector Warning: No annotation types given for processing");
      return;
    }

    // get the annotations from document
    if ((markupSetName == null)|| (markupSetName.equals("")))
      allAnnots = document.getAnnotations();
    else
      allAnnots = document.getAnnotations(markupSetName);

    //if none found, print warning and exit
    if ((allAnnots == null) || allAnnots.isEmpty()) {
      Out.prln("Gazetteer Lists Collector Warning: No annotations found for processing");
      return;
    }

    //collect the stats for each annotation type
    for (int i = 0; i < annotationTypes.size(); i++) {
      AnnotationSet annots = allAnnots.get((String) annotationTypes.get(i));
      if (annots == null || annots.isEmpty())
        continue;
      statsPerType.put(annotationTypes.get(i), new HashMap());
      collectLists(annots, (String) annotationTypes.get(i));
    }

    //print out the stats in log files
    printStats();

    //save the updated gazetteer lists now
    Map theLists = gazetteer.getLinearDefinition().getListsByNode();
    Iterator iter1 = theLists.keySet().iterator();
    while (iter1.hasNext()) {
      GazetteerList theList = (GazetteerList) theLists.get(iter1.next());
      try {
        if (theList.isModified())
          theList.store();
      } catch (ResourceInstantiationException ex) {
        throw new GateRuntimeException(ex.getMessage());
      }
    }

  }

  public void setMarkupASName(String newMarkupASName) {
    markupSetName = newMarkupASName;
  }

  public String  getMarkupASName() {
    return markupSetName;
  }

  /** get the types of the annotation
   * @return type of the annotation
   */
  public List getAnnotationTypes() {
    return annotationTypes;
  }//getAnnotationTypes

  /** set the types of the annotations
   * @param newType 
   */
  public void setAnnotationTypes(List newType) {
    annotationTypes = newType;
  }//setAnnotationTypes

  public Gazetteer getGazetteer() {
    return gazetteer;
  }

  public void setGazetteer(Gazetteer theGaz) {
    gazetteer = theGaz;
  }

  public void setTheLanguage(String language) {
    theLanguage = language;
  }

  public String  getTheLanguage() {
    return theLanguage;
  }

  protected void collectLists(AnnotationSet annots, String annotType) {
    Iterator<Annotation> iter = annots.iterator();
    String listName = "";
    GazetteerList theList = null;
    Iterator theListsIter =
      gazetteer.getLinearDefinition().getListsByNode().values().iterator();
    while (theListsIter.hasNext() && listName.equals("")) {
      theList = (GazetteerList) theListsIter.next();
      if (theList.getURL().toExternalForm().endsWith(annotType + ".lst"))
        listName = theList.getURL().toExternalForm();
    }
    while (iter.hasNext()) {
      Annotation annot = iter.next();
      String text = "";
      List strings = new ArrayList();
      try {
        text = document.getContent().getContent(
          annot.getStartNode().getOffset(),
          annot.getEndNode().getOffset()
        ).toString();
        //tokenise the text and save for the future if we need it
        StringTokenizer tok = new StringTokenizer(text, "\n\r.|();-?!\t", false);
        while (tok.hasMoreTokens())
          strings.add(tok.nextToken());
        //then replace the line breaks with spaces for the gazetteer
        text = text.replace('\r', ' ');
        text = text.replace('\n', ' ');
        text = text.replace('\t', ' ');

      } catch (InvalidOffsetException ex) {
        throw new GateRuntimeException(ex.getMessage());
      }

      //collect stats for the string
      if (((HashMap) statsPerType.get(annotType)).containsKey(text))
        ((HashMap) statsPerType.get(annotType)).put(text,
            new Integer(((Integer)
              ((HashMap) statsPerType.get(annotType)).get(text)).intValue()+1));
      else
        ((HashMap) statsPerType.get(annotType)).put(text, new Integer(1));

      //also collect stats for the individual tokens in the name to identify the most
      //frequent tokens across names
      if (strings.size() > 1) {
        for (int i=0; i < strings.size(); i++) {
          String theString = (String) strings.get(i);
          //collect stats for the string
          if ( ( (HashMap) statsPerType.get(annotType)).containsKey(theString))
            ( (HashMap) statsPerType.get(annotType)).put(theString,
                new Integer( ( (Integer)
                              ( (HashMap) statsPerType.get(annotType)).get(
                theString)).intValue() + 1));
          else
            ( (HashMap) statsPerType.get(annotType)).put(theString,
                new Integer(1));
        }
      }

      //first we check whether the text is already in the gazetteer
      Set lookupResult = gazetteer.lookup(text);
      if (lookupResult != null && lookupResult.size() > 0)
        continue;
      //if not, then we add it
      gazetteer.add(text,
        new Lookup(listName, annotType, "inferred", theLanguage));
//      theList.add(text + document.getSourceUrl().toString());
      theList.add(new GazetteerNode(text));


      //for persons we want also to add their individual names to the list
      if (annotType.equals(PERSON_ANNOT_NAME) && strings.size() > 1) {
        for (int i=0; i < strings.size(); i++) {
          String theString = (String) strings.get(i);
          Set lookupResult1 = gazetteer.lookup(theString);
          if (lookupResult1 != null && lookupResult1.size() > 0)
            continue;
          if (theString.length() < 3)
            continue;
          gazetteer.add(theString,
            new Lookup(listName, annotType, "inferred", theLanguage));
          theList.add(new GazetteerNode(theString));
        }
      }
    }
  }

  protected void printStats() {
    try {
      for (int i=0; i < annotationTypes.size(); i++) {
        if (! statsPerType.containsKey(annotationTypes.get(i)))
          continue;
        BufferedWriter writer = new BufferedWriter(
          new OutputStreamWriter(new FileOutputStream(
           annotationTypes.get(i) + ".stats.lst"),
          "UTF-8"));
        HashMap stats = (HashMap) statsPerType.get(annotationTypes.get(i));
        Iterator stringsIter = stats.keySet().iterator();
        while (stringsIter.hasNext()) {
          String string = (String) stringsIter.next();
          writer.write(string);
          writer.write("$");
          writer.write( ((Integer)stats.get(string)).toString());
          writer.newLine();
        }
        writer.close();
      }
  } catch(IOException ioe){
      throw new RuntimeException(ioe.getMessage());
  }//try

  }

  /**
   * The idea is to have this method check if an item
   * is already present in the gazetteer under this type,
   * and if so, not to add it. It is not implemented for now.
   */
  protected boolean alreadyPresentInGazetteer(String token) {
    return false;
  }

  private String markupSetName = "";
  private AnnotationSet allAnnots;
  private List annotationTypes;
  private Gazetteer gazetteer;
  private String theLanguage = "";
  private HashMap statsPerType = new HashMap();
}