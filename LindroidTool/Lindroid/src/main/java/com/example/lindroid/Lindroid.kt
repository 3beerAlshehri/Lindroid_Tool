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

    fun scanFiles() {
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
            val textTag = nodeList.item(i)
            //if the node is an element.
            if (textTag.nodeType == Node.ELEMENT_NODE) {
                val textElement = textTag as Element
                //Read text size
                val el_size = textElement.getAttribute("android:textSize")

                //if there is attribute
                if (!el_size.isEmpty()) {
                    val text_size = el_size.substring(0, 2)

//******************/\/\/\/\/\/\/\/\/\/\/\/\..FIRST RULES: TEXT SIZE >= 31sp../\/\/\/\/\/\/\/\/\/\/\
                    try {
                        if (text_size.toInt() < 31) {
                            wCounter++
                            Counter++
                            print("Issue # $Counter")
                            println(": <Warning> in line " + textTag.getUserData("lineNumber") + ": The text size of <" + textTag.getNodeName() +
                                    "> is \"" + el_size + "\", it must be not less than \"31\".."
                            )
                        }
                    } catch (e: Exception) {
                    }
                } //________________________________________________________________________\\

//******************/\/\/\/\/\/\/\/\/\/\/\/\..SECOND RULES: ALL COMPONENT OF THE ACTIVITY MUST HAVE A LABEL../\/\/\
//__________________________________________..text fields must have hint not contentDescription..__________________

                //Read content description of the element
                val el_contentDescription = textElement.getAttribute("android:contentDescription")
                if (textTag.getNodeName() == "EditText" || textTag.getNodeName() == "AutoCompleteTextView" || textTag.getNodeName() == "MultiAutoCompleteTextView" || textTag.getNodeName() == "com.google.android.material.textfield.TextInputEditText") {
                    //Read hint of the element
                    val el_hint = textElement.getAttribute("android:hint")

                    //if there is contentDescription
                    if (!el_contentDescription.isEmpty()) {
                        vCounter++
                        Counter++
                        print("Issue # $Counter")
                        print(": <Violation> in line " + textTag.getUserData("lineNumber") + ": the component <" + textTag.getNodeName())
                        println("Input fields should have their speakable text set as “hints”, not “content description”. \n" +
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
                            println(": <Violation> in line " + textTag.getUserData("lineNumber") + ": duplicate label \"" + el_hint + "\" in <" + textTag.getNodeName() + ">")
                        } else hints.add(el_hint)
                    } else {
                        pvCounter++
                        Counter++
                        print("Issue # $Counter")
                        println(": <Potential violation> in line " + textTag.getUserData("lineNumber") + ": Missing \"hint\" to provide instructions on how to fill the data entry field for the component: <" + textTag.getNodeName() + ">")
                    }

//*****************/\/\/\/\/\/\/\/\/\/\/\/\..FOURTH RULES: PROVIDE FIELD FILL-IN TIPS TO AVOID INCREASING THE VISUALLY IMPAIRED USER INTERACTION LOAD DUE TO INCORRECT INPUT.

                    //Read fill in tips of the element
                    val el_text = textElement.getAttribute("android:text")
                    if (el_text.isEmpty()) {
                        wCounter++
                        Counter++
                        print("Issue # $Counter")
                        println(": <Warning> in line " + textTag.getUserData("lineNumber") + ": Try to write text tips to help user to fill field in component <" + textTag.getNodeName() + ">")
                    }
                }

//******************/\/\/\/\/\/\/\/\/\/\/\/\..FIFTH RULES: WARN IF THE IMAGE CONTAIN TEXT../\/\/\/\/\/\/\/\/\/\/\
                if (textTag.getNodeName() == "ImageView") {
                    pvCounter++
                    Counter++
                    print("Issue # $Counter")

                    print(": <Potential violation> in line " + textTag.getUserData("lineNumber") + ": In the component <" + textTag.getNodeName())
                    println("> Does the image you inserted contain text? \nIf the answer is \"yes\", this image is not accessible to persons with disabilities.")
                }

//******************/\/\/\/\/\/\/\/\/\/\/\/\..SIXTH RULES: THE BUTTON AND OTHER CLICKABLE ELEMENTS SIZE NOT LESS THAN "57dp" HIGHT AND "57dp" WIDTH.
                if (textTag.getNodeName() == "Button" || textTag.getNodeName() == "ImageButton" || textTag.getNodeName() == "RadioButton" || textTag.getNodeName() == "CheckBox" || textTag.getNodeName() == "Switch" || textTag.getNodeName() == "ToggleButton" || textTag.getNodeName() == "com.google.android.material.floatingactionbutton.FloatingActionButton") {
                    //Read width of the element.
                    val el_width = textElement.getAttribute("android:layout_width")
                    //Read hight of the element.
                    val el_height = textElement.getAttribute("android:layout_height")
                    if (!(el_width.equals("wrap_content", ignoreCase = true) || el_width.equals("match_parent", ignoreCase = true))) {
                        val index = el_width.indexOf("d")
                        if (el_width.substring(0, index).toInt() < 57) {
                            wCounter++
                            Counter++
                            print("Issue # $Counter")

                            println(": <Warning> in line " + textTag.getUserData("lineNumber") + ": The width size of <" + textTag.getNodeName() +
                                    "> is \"" + el_width + "\", it must be not less than \"57dp\"")
                        }
                    }
                    if (!(el_height.equals("wrap_content", ignoreCase = true) || el_height.equals("match_parent", ignoreCase = true))) {
                        val index = el_height.indexOf("d")
                        if (el_height.substring(0, index).toInt() < 57) {
                            wCounter++
                            Counter++
                            print("Issue # $Counter")
                            println(": <Warning> in line " + textTag.getUserData("lineNumber") + ": The height size of <" + textTag.getNodeName() +
                                    "> is \"" + el_height + "\", it must be not less than \"57dp\"")
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

                            println(": <Violation> in line " + textTag.getUserData("lineNumber") + ": duplicate label \"" + el_contentDescription + "\" in <" + textTag.getNodeName() + ">")
                        } else contents.add(el_contentDescription)
                    } else {
                        pvCounter++
                        Counter++
                        print("Issue # $Counter")
                        println(": <Potential violation> in line " + textTag.getUserData("lineNumber") + ": Missing \"contentDescription\" for the component: <" + textTag.getNodeName() + ">")
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