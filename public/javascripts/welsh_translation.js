function initLocalisedContent() {

  "use strict";

  var content = {
    "en" : {
      "ogl.pt.1" : "All content is available under the",
      "ogl.link.text" : "Open Government Licence v3.0",
      "ogl.pt.2" : ", except where otherwise stated"
    },
    "cy" : {
      "ogl.pt.1" : "WELSH All content is available under the",
      "ogl.link.text" : "WELSH Open Government Licence v3.0",
      "ogl.pt.2" : ", WELSH except where otherwise stated"
    }
  }

  String.prototype.supplant = function (o) {
    return this.replace(/{([^{}]*)}/g,
      function (a, b) {
        var r = o[b];
        return typeof r === 'string' || typeof r === 'number' ? r : a;
      }
    );
  };

  GOVUK.playLanguage = (function() {
    var playCookieName = encodeURIComponent("PLAY_LANG") + "=";
    var ca = document.cookie.split(';');
    for (var i = 0; i < ca.length; i++) {
      var c = ca[i];
      while (c.charAt(0) === ' ')
        c = c.substring(1, c.length);
        if (c.indexOf(playCookieName) === 0)
          return decodeURIComponent(c.substring(playCookieName.length, c.length));
    }
    return "en";
  }());

  GOVUK.getLocalisedContent = function(key, args) {
    return content[GOVUK.playLanguage][key].supplant(args);
  }
}

$(document).ready(function () {

  initLocalisedContent();

  // Switch out OGL footer if welsh language
  if (GOVUK.playLanguage === "cy") {
    $("div.open-government-licence > p").remove();
    $("div.open-government-licence").append('<p>' +
      GOVUK.getLocalisedContent("ogl.pt.1") +
      ' <a href="http://www.nationalarchives.gov.uk/doc/open-government-licence/version/3" target="_blank">' +
      GOVUK.getLocalisedContent("ogl.link.text") +
      '</a>' +
      GOVUK.getLocalisedContent("ogl.pt.2") +
      '</p>');
  }
});