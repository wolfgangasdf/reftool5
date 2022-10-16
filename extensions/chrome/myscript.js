// content script to get the pdf from cache that is shown in google chrome's builtin pdf viewer
// this doesn't work with pdf.js extension, see background.js
// http://stackoverflow.com/a/29338361

// note that log from here via console.log is going to the tab's console, not that of background.js!
// BUT open console from page BEFORE showing pdf, or using view->developer-js console.

console.log("reftool5extension: url = " + window.location.href);

try {
    // get last frame for damn osa journals etc.
    var mydoc = document;
    var frames = document.getElementsByTagName("frame");
    if (frames.length > 0) {
        console.log("reftool5extension: have frames (" + frames.length + "), using last one.");
        var theframe = frames[frames.length - 1];
        console.log("reftool5extension:  frame = ", theframe);
        console.log("reftool5extension:  frame url = ", theframe.contentWindow.location.href);
        // this gives exception if pdf.js plugin used... see no solution right now.
        // but this only happens for opticsinfobase iframe-stuff.
        mydoc = theframe.contentDocument || theFrame.contentWindow.document;
    }

    var doit = /pdf/i.test(window.location.href.slice(-3)); // for normal chrome pdf viewer
    console.log("reftool5extension: doit=", doit);
    if (!doit) {
        var embeds = mydoc.querySelectorAll("embed");
        console.log("reftool5extension: embeds = ", embeds);
        if (embeds.length > 0) {
            if (embeds[0].type === "application/pdf") {
                doit = true;
            }
        } else {
            console.log("reftool5extension: can't find embed object")
        }
    }
    doit = true;
    if (doit) {
        console.log("reftool5extension: initiating download via xhr...");

        // download pdf from chrome cache
        var xhr = new XMLHttpRequest();
        xhr.open("GET", "", true);
        xhr.responseType = "blob";
        xhr.onload = function (e) {
            console.log("reftool5extension: onload: status = " + this.status);
            if (this.status === 200) {
                console.log("reftool5extension: response: " + this.response);
                var file = window.URL.createObjectURL(this.response);
                var a = mydoc.createElement("a");
                a.href = file;
                a.download = "reftool5import.pdf";
                mydoc.body.appendChild(a);
                a.click();
                // remove `a` following `Save As` dialog,
                // `window` regains `focus`
                window.onfocus = function () {
                    Array.prototype.forEach.call(document.querySelectorAll("a"),
                    function (el) {
                        document.body.removeChild(el);
                    });
                };
            }
        };
        xhr.send();
    }

} catch(err) {
    alert("Reftool import error: " + err.message);
    console.log("reftool5extension: ERROR " + err.message + "\n");
}

