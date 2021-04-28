package com.example.lindroid

import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.Node
import org.xml.sax.Attributes
import org.xml.sax.Locator
import org.xml.sax.SAXException
import org.xml.sax.helpers.DefaultHandler
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.io.InputStream
import java.util.*
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.parsers.ParserConfigurationException
import javax.xml.parsers.SAXParser
import javax.xml.parsers.SAXParserFactory

object Lindroid {
    var folderPath = System.getProperty("user.dir") + "/app/src/main/res/"
    var Counter = 0
    var fileCounter = 0
    const val LINE_NUMBER_KEY_NAME = "lineNumber"

    fun checkAccessibility() {
        listFilesForFolder()
        println("\nTOTAL RESULT: --> You Have $Counter Accessibility Issues In ${fileCounter} Files")
    }


    @Throws(IOException::class, SAXException::class)
    private fun listFilesForFolder(folder: File? = File(folderPath)) {
        try {
            folder?.listFiles()!!.forEachIndexed { index, fileEntry ->
                if (fileEntry.isDirectory) {
                    listFilesForFolder(fileEntry)
                } else {
                    if (fileEntry.name.endsWith(".xml")) {
                        fileCounter++
                        parseXMLfile(fileEntry)
                    } else {
                        //System.out.println(fileEntry.getParent());
                    }
                }
            }
        } catch (e: Exception) {
        }
    }

    @Throws(IOException::class, SAXException::class)
    fun readXML(`is`: InputStream?): Document {
        val doc: Document
        val parser: SAXParser
        try {
            val factory = SAXParserFactory.newInstance()
            parser = factory.newSAXParser()
            val Factory = DocumentBuilderFactory.newInstance()
            val Builder = Factory.newDocumentBuilder()
            doc = Builder.newDocument()
        } catch (e: ParserConfigurationException) {
            throw RuntimeException("Can't create SAX parser / DOM builder.", e)
        }
        val elementStack = Stack<Element>()
        val textBuffer = StringBuilder()
        val handler: DefaultHandler = object : DefaultHandler() {
            private var locator: Locator? = null
            override fun setDocumentLocator(locator: Locator) {
                this.locator =
                        locator //Save the locator, so that it can be used later for line tracking when traversing nodes.
            }

            override fun startElement(
                    uri: String,
                    localName: String,
                    qName: String,
                    attributes: Attributes
            ) {
                addTextIfNeeded()
                val el = doc.createElement(qName)
                for (i in 0 until attributes.length) el.setAttribute(
                        attributes.getQName(i),
                        attributes.getValue(i)
                )
                el.setUserData(LINE_NUMBER_KEY_NAME, locator!!.lineNumber.toString(), null)
                elementStack.push(el)
            }

            override fun endElement(uri: String, localName: String, qName: String) {
                addTextIfNeeded()
                val closedEl = elementStack.pop()
                if (elementStack.isEmpty()) { // Is this the root element?
                    doc.appendChild(closedEl)
                } else {
                    val parentEl = elementStack.peek()
                    parentEl.appendChild(closedEl)
                }
            }

            override fun characters(ch: CharArray, start: Int, length: Int) {
                textBuffer.append(ch, start, length)
            }

            // Outputs text accumulated under the current node
            private fun addTextIfNeeded() {
                if (textBuffer.length > 0) {
                    val el = elementStack.peek()
                    val textNode: Node = doc.createTextNode(textBuffer.toString())
                    el.appendChild(textNode)
                    textBuffer.delete(0, textBuffer.length)
                }
            }
        }
        parser.parse(`is`, handler)
        return doc
    }

    @Throws(IOException::class, SAXException::class)
    fun parseXMLfile(xmlFile: File) {
        //String filePath = folderPath + "\\" + xmlFile.getName();

        //Array to save labels in one Activity, to prevent duplicate labels.
        val hints: ArrayList<String> = ArrayList<String>()
        val contents: ArrayList<String> = ArrayList<String>()
        val items: ArrayList<String> = ArrayList<String>()
        //For count violations, potential violations, warnings in one Activity
        var vCounter = 0
        var pvCounter = 0
        var wCounter = 0

        val `is`: InputStream = FileInputStream(xmlFile)
        val document = readXML(`is`)
        `is`.close()
        /*
        // Get the Document Builder
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder = factory.newDocumentBuilder();

        // Get Document
        Document document = builder.parse (new File(filePath));
        */

        //Normalize the xml structure
        document.documentElement.normalize()

        //Get all the element by the tag name
        val nodeList = document.getElementsByTagName("*")

        //Loop to start parser.
        for (i in 0 until nodeList.length) {
            val Tag = nodeList.item(i)
            //if the node is an element.
            if (Tag.nodeType == Node.ELEMENT_NODE) {
                val element = Tag as Element
                //Read text size
                val el_size = element.getAttribute("android:textSize")

                //if there is attribute
                if (!el_size.isEmpty()) {
                    val text_size = el_size.substring(0, 2)

//******************/\/\/\/\/\/\/\/\/\/\/\/\..FIRST RULES: TEXT SIZE >= 31sp../\/\/\/\/\/\/\/\/\/\/\
                    try {
                        if (text_size.toInt() < 31) {
                            wCounter++
                            Counter++
                            print("Issue # $Counter")
                            println(": <Warning> in line " + Tag.getUserData("lineNumber") + ": The text size of <" + Tag.getNodeName() +
                                    "> is \"" + el_size + "\", For ACCESSIBILITY: it must be not less than \"31\".."
                            )
                        }
                    } catch (e: Exception) {
                    }
                } //________________________________________________________________________\\

//******************/\/\/\/\/\/\/\/\/\/\/\/\..SECOND RULES: ALL COMPONENT OF THE ACTIVITY MUST HAVE A LABEL../\/\/\
//__________________________________________..text fields must have hint not contentDescription..__________________

                //Read content description of the element
                val el_contentDescription = element.getAttribute("android:contentDescription")
                if (Tag.getNodeName() == "EditText" || Tag.getNodeName() == "AutoCompleteTextView" || Tag.getNodeName() == "MultiAutoCompleteTextView" || Tag.getNodeName() == "com.google.android.material.textfield.TextInputEditText") {
                    //Read hint of the element
                    val el_hint = element.getAttribute("android:hint")

                    //if there is contentDescription
                    if (!el_contentDescription.isEmpty()) {
                        vCounter++
                        Counter++
                        print("Issue # $Counter")
                        print(": <Violation> in line " + Tag.getUserData("lineNumber") + ": the component <" + Tag.getNodeName())
                        println("For ACCESSIBILITY: Input fields should have their speakable text set as “hints”, not “content description”. \n" +
                                "    If the content description property is set, the screen reader will read it even when the " +
                                "input field is not empty, which could confuse the user who might not know what part is\n" +
                                "    the text in the input field and which part is the content description.")
                    }
                    //if there is hint
                    if (!el_hint.isEmpty()) {

//******************/\/\/\/\/\/\/\/\/\/\/\/\..THIRD RULES: THE LABELS NOT DUPLICATE IN ONE ACTIVITY../\/\/\/\/\/\

                        //Check hint in arraylist, if it exist, there is duplicate, print warning
                        if (hints.contains(el_hint)) {
                            vCounter++
                            Counter++
                            print("Issue # $Counter")
                            println(": <Violation> in line " + Tag.getUserData("lineNumber") + ": For ACCESSIBILITY, this is duplicate label \"" + el_hint + "\" in <" + Tag.getNodeName() + ">")
                        } else hints.add(el_hint)
                    } else {
                        pvCounter++
                        Counter++
                        print("Issue # $Counter")
                        println(": <Potential violation> in line " + Tag.getUserData("lineNumber") + ": For ACCESSIBILITY, set \"hint\" to provide instructions on how to fill the data entry field for the component: <" + Tag.getNodeName() + ">")
                    }

//*****************/\/\/\/\/\/\/\/\/\/\/\/\..FOURTH RULES: PROVIDE FIELD FILL-IN TIPS TO AVOID INCREASING THE VISUALLY IMPAIRED USER INTERACTION LOAD DUE TO INCORRECT INPUT.

                    //Read fill in tips of the element
                    val el_text = element.getAttribute("android:text")
                    //if there is no tip
                    if (el_text.isEmpty()) {
                        wCounter++
                        Counter++
                        print("Issue # $Counter")
                        println(": <Warning> in line " + Tag.getUserData("lineNumber") + ": For ACCESSIBILITY, Try to write text tips to help user to fill field in component <" + Tag.getNodeName() + ">")
                    }
                }

                if (Tag.getNodeName() == "TextView"){
                    //if there is attribute
                    if (!el_size.isEmpty()) {
                        val text_size = el_size.substring(0, 2)

//******************/\/\/\/\/\/\/\/\/\/\/\/\..FIRST RULES: TEXT SIZE >= 31sp../\/\/\/\/\/\/\/\/\/\/\
                        try {
                            if (text_size.toInt() < 31) {
                                wCounter++
                                Counter++
                                print("Issue # $Counter")
                                println(": <Warning> in line " + Tag.getUserData("lineNumber") + ": The text size of <" + Tag.getNodeName() +
                                        "> is \"" + el_size + "\", For ACCESSIBILITY: it must be not less than \"31\".."
                                )
                            }
                        } catch (e: Exception) {
                        }
                    } //________________________________________________________________________\\
                }

//******************/\/\/\/\/\/\/\/\/\/\/\/\..FIFTH RULES: WARN IF THE IMAGE CONTAIN TEXT../\/\/\/\/\/\/\/\/\/\/\
                if (Tag.getNodeName() == "ImageView") {
                    pvCounter++
                    Counter++
                    print("Issue # $Counter")

                    print(": <Potential violation> in line " + Tag.getUserData("lineNumber") + ": In the component <" + Tag.getNodeName())
                    println("> For ACCESSIBILITY, be careful about 3 issues:\n" +
                            "1.\tIf the image for decorative purpose: not use contentDescription.\n" +
                            "2.\tIf the image contains text information: it is not accessible to persons with disabilities.\n" +
                            "3.\tOtherwise, it must has a clear description.\n")
                }
//******************/\/\/\/\/\/\/\/\/\/\/\/\..THIRD RULES: THE LABELS NOT DUPLICATE IN ONE ACTIVITY../\/\/\/\/\/\
                if (Tag.getNodeName() == "menu"){
                    if (Tag.getNodeName() == "item"){
                        val el_item = element.getAttribute("android:title")
                        //if there is title
                        if (!el_item.isEmpty()) {
                            //Check title in arraylist, if it exist, there is duplicate, print warning
                            if (items.contains(el_item)) {
                                vCounter++
                                Counter++
                                print("Issue # $Counter")
                                println(": <Violation> in line " + Tag.getUserData("lineNumber") + ": For ACCESSIBILITY, this is duplicate label \"" + el_item + "\" in <" + Tag.getNodeName() + ">")
                            } else items.add(el_item)
                        } else {
                            pvCounter++
                            Counter++
                            print("Issue # $Counter")
                            println(": <Potential violation> in line " + Tag.getUserData("lineNumber") + ": For ACCESSIBILITY, set \"title\" to provide clear lable for the component: <" + Tag.getNodeName() + ">")
                        }
                    }
                }
//******************/\/\/\/\/\/\/\/\/\/\/\/\..SIXTH RULES: THE BUTTON AND OTHER CLICKABLE ELEMENTS SIZE NOT LESS THAN "57dp" HIGHT AND "57dp" WIDTH.
                if (Tag.getNodeName() == "Button" || Tag.getNodeName() == "ImageButton" || Tag.getNodeName() == "RadioButton" || Tag.getNodeName() == "CheckBox" || Tag.getNodeName() == "Switch" || Tag.getNodeName() == "ToggleButton" || Tag.getNodeName() == "com.google.android.material.floatingactionbutton.FloatingActionButton") {
                    //Read width of the element.
                    val el_width = element.getAttribute("android:layout_width")
                    //Read hight of the element.
                    val el_height = element.getAttribute("android:layout_height")
                    if (!(el_width.equals("wrap_content", ignoreCase = true) || el_width.equals("match_parent", ignoreCase = true))) {
                        val index = el_width.indexOf("d")
                        if (el_width.substring(0, index).toInt() < 57) {
                            wCounter++
                            Counter++
                            print("Issue # $Counter")

                            println(": <Warning> in line " + Tag.getUserData("lineNumber") + ": The width size of <" + Tag.getNodeName() +
                                    "> is \"" + el_width + "\"For ACCESSIBILITY, it must be not less than \"57dp\"")
                        }
                    }
                    if (!(el_height.equals("wrap_content", ignoreCase = true) || el_height.equals("match_parent", ignoreCase = true))) {
                        val index = el_height.indexOf("d")
                        if (el_height.substring(0, index).toInt() < 57) {
                            wCounter++
                            Counter++
                            print("Issue # $Counter")
                            println(": <Warning> in line " + Tag.getUserData("lineNumber") + ": The height size of <" + Tag.getNodeName() +
                                    "> is \"" + el_height + "\"For ACCESSIBILITY, it must be not less than \"57dp\"")
                        }
                    }
                    //check if contentDescription missing or duplicated.
                    //if there is contentDescription
                    if (!el_contentDescription.isEmpty()) {

                        //Check contentDescription in arraylist, if it exist, there is duplicate, print warning
                        if (contents.contains(el_contentDescription)) {
                            vCounter++
                            Counter++
                            print("Issue # $Counter")

                            println(": <Violation> in line " + Tag.getUserData("lineNumber") + ": For ACCESSIBILITY, this is duplicate label \"" + el_contentDescription + "\" in <" + Tag.getNodeName() + ">")
                        } else contents.add(el_contentDescription)
                    } else {
                        pvCounter++
                        Counter++
                        print("Issue # $Counter")
                        println(": <Potential violation> in line " + Tag.getUserData("lineNumber") + ": For ACCESSIBILITY, set \"contentDescription\" for the component: <" + Tag.getNodeName() + ">")
                    }
                }

            }
        }

        if (vCounter != 0 || pvCounter != 0 || wCounter != 0) {
            println("^")
            println("^ YOU HAVE: $vCounter <Violations>, $pvCounter <Potential violations>, $wCounter <Warnings>,  IN  -->  ${xmlFile.parent}/${xmlFile.name} ")
            println("----------------------------------------------------------------------------------------------\n")
        }
    }
}