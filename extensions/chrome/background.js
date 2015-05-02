
chrome.browserAction.onClicked.addListener(function(tab) {

  chrome.tabs.executeScript(null,
    { file: "myscript.js" },
    function(resultx){
        var data = resultx[0][0];
        console.log("data=" + data);
        var log = resultx[0][1];
        var lines = String(log).split('\n');
        for (var s of lines) {
            console.log("[myscript] " + s);
        }
    }
  );
});
