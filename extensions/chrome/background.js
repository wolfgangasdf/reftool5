// download currently viewed pdf

chrome.browserAction.onClicked.addListener(function(tab) {
    var tablink = "xxx";
    chrome.tabs.getSelected(null,function(tab) {
        tablink = tab.url;
        console.log("tab url=" + tablink);
        if (tablink.lastIndexOf("chrome-extension://", 0) === 0) {
            console.log("chrome extension in use, have to download without using cache!")
            var pdfsource = tablink.replace(/chrome-extension:\/\/\w+\//, '');
            if (/pdf/i.test(window.location.href.slice(-3))) {
                saveit(pdfsource);
            } else {
                console.log("not a pdf document: " + pdfsrouce)
            }
        } else { // google chrome internal pdf viewer used (wrong for opticsinfobase with pdf.js)
            chrome.tabs.executeScript(null,
            { file: "myscript.js" },
            function(resultx){
                var data = resultx[0][0];
                console.log("data=" + data);
                var log = resultx[0][1];
                var lines = String(log).split('\n');
                for (var s of lines) {
                    console.log("[log] " + s);
                }
            }
            );
        }

    });

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
