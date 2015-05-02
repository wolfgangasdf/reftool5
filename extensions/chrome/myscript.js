// get the pdf from cache that is shown in google chrome's builtin pdf viewer
// http://stackoverflow.com/a/29338361

var log = ""
var pdfsource  = ""

function logit(s) {
    log += s + "\n";
}

try {
    // get last frame for damn osa journals etc.
    var mydoc = document
    var frames = document.getElementsByTagName("frame")
    if (frames.length > 0) {
        logit("have frames (" + frames.length + "), using last one.");
        var theframe = frames[frames.length - 1];
        logit("  theframe = " + theframe.contentDocument);
        mydoc = theframe.contentDocument || theFrame.contentWindow.document;
    }

    var doit = /pdf/i.test(window.location.href.slice(-3));
    if (!doit) {
        var embeds = mydoc.querySelectorAll("embed");
        console.log("embeds" + embeds);
        if (embeds.length > 0) {
            doit = true;
        }
    }

    if (doit) {
        logit("doit!");
        pdfsource = mydoc.querySelectorAll("embed")[0].src;
        logit("embedthing src: " + pdfsource);

        // load from chrome cache
        var xhr = new XMLHttpRequest();
        // load `document` from `cache`
        xhr.open("GET", pdfsource, true);
        xhr.responseType = "blob";
        xhr.onload = function (e) {
            if (this.status === 200) {
                var file = window.URL.createObjectURL(this.response);
                var a = mydoc.createElement("a");
                a.href = file;
                a.download = "reftool5import.pdf" //this.response.name || pdfsource.split("/").pop();
                mydoc.body.appendChild(a);
                a.click();
            };
        };
        xhr.send();
    };

} catch(err) {
    log += "ERROR" + err.message + "\n";
}


[ pdfsource, log ]