// download currently viewed pdf
// debug: console, myscript log goes to the tab's console (load console log BEFORE showing pdf or via menu!)

chrome.action.onClicked.addListener(tab => {
    console.log("onclick! ", tab);

    var tablink = tab.url;
    console.log("tab url=" + tablink);
    if (tablink.lastIndexOf("chrome://extensions/", 0) === 0) {
        console.log("active tab is chrome extensions tab")
    } else if (tablink.lastIndexOf("chrome-extension://", 0) === 0) {
        console.log("chrome extension in use, have to download without using cache!")
        var pdfsource = tablink.replace(/chrome-extension:\/\/\w+\//, '');
        if (/pdf/i.test(window.location.href.slice(-3))) {
            console.log("saving ", pdfsource);
            saveit(pdfsource);
        } else {
            console.log("not a pdf document: " + pdfsrouce)
        }
    } else { // google chrome internal pdf viewer used? try!
        console.log("internal chrome pdf viewer? run myscript in tab: ", tab);
        chrome.scripting.executeScript({ // inject script into DOM (background.js can't access DOM)
            target: {tabId: tab.id},
            files: ["myscript.js"]
        }, (e) => {
            console.log("result: ", typeof e);
            console.log("result: ", e);
        });
        // could return something on success, if it doesn't work, use saveit below.
    }

    // download file using chrome's downloads api (needs 'downloads' permission)
    // this does not use google chrome cache :-(
    function saveit(pdfsource) {
        if (pdfsource != "") {
            console.log("downloading " + pdfsource + " ...");
            chrome.downloads.download({
              url: pdfsource,
              filename: "reftool5import.pdf"
            });
        }
    }
});
