// get the pdf from cache that is shown in google chrome's builtin pdf viewer
// this doesn't work with pdf.js extension, see background.js
// http://stackoverflow.com/a/29338361

var log = ""
var pdfsource  = ""

function logit(s) { // cannot write to console
    log += s + "\n";
}

try {
    logit("url = " + window.location.href);

    // get last frame for damn osa journals etc.
    var mydoc = document;
    var frames = document.getElementsByTagName("frame");
    if (frames.length > 0) {
        logit("have frames (" + frames.length + "), using last one.");
        var theframe = frames[frames.length - 1];
        logit(" frame = " + theframe);
        logit(" frame url = " + theframe.contentWindow.location.href);
        // this gives exception if pdf.js plugin used... see no solution right now.
        // but this only happens for opticsinfobase iframe-stuff.
        mydoc = theframe.contentDocument || theFrame.contentWindow.document;
    }

    var doit = /pdf/i.test(window.location.href.slice(-3));
    if (!doit) {
        var embeds = mydoc.querySelectorAll("embed");
        logit("embeds = " + embeds);
        if (embeds.length > 0) {
            doit = true;
        }
    }

    if (doit) {
        logit("initiating download...");
        logit("embed pdfsource: " + mydoc.querySelectorAll("embed")[0].src);
        logit("embed type: " + mydoc.querySelectorAll("embed")[0].type);

        // download pdf from chrome cache
        var xhr = new XMLHttpRequest();
        xhr.open("GET", "", true);
        xhr.responseType = "blob";
        xhr.onload = function (e) {
            logit("onload: status = " + this.status);
            if (this.status === 200) {
                logit("response: " + this.response);
                var file = window.URL.createObjectURL(this.response);
                var a = mydoc.createElement("a");
                a.href = file;
                a.download = "reftool5import.pdf";
                mydoc.body.appendChild(a);
                a.click();
                // remove `a` following `Save As` dialog,
                // `window` regains `focus`
                window.onfocus = function () {
                    Array.prototype.forEach.call(document.querySelectorAll("a")
                    , function (el) {
                        document.body.removeChild(el);
                    })
                }
            };
        };
        xhr.send();
    };

} catch(err) {
    alert("Reftool import error: " + err.message)
    log += "ERROR " + err.message + "\n";
}


[ "", log ]