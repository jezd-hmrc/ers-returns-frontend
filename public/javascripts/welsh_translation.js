function initLocalisedContent() {

  "use strict";

  var content = {
    "en" : {
      "ogl.pt.1" : "All content is available under the",
      "ogl.link.text" : "Open Government Licence v3.0",
      "ogl.pt.2" : ", except where otherwise stated",
      "crown.copyright" : "© Crown Copyright",
      "ers.file.upload.wrong.file" : "This is not a file that you said you needed to upload, choose a different file",
      "ers.file.upload.csv.file.large" : "This file is larger than 100MB – choose a different file or email <a href='mailto:shareschemes@hmrc.gsi.gov.uk'>shareschemes@hmrc.gsi.gov.uk</a> and we will help you submit your return",
      "ers.file.upload.csv.wrong.type" : "This file is not a .csv file, choose a different file",
      "ers.file.upload.ods.large" : "This file is larger than 10MB – choose a different file or email <a href='mailto:shareschemes@hmrc.gsi.gov.uk'>shareschemes@hmrc.gsi.gov.uk</a> and we will help you submit your return",
      "ers.file.upload.ods.wrong.type" : "This file is not a .ods file, choose a different file",
      "ers.file.upload.ods.too.long" : "The filename must contain 240 characters or less",
      "ers.file.upload.ods.invalid.characters" : "The filename contains invalid characters"
    },
    "cy" : {
      "ogl.pt.1" : "Mae'r holl gynnwys ar gael dan y",
      "ogl.link.text" : "Drwydded Llywodraeth Agored, fersiwn 3.0",
      "ogl.pt.2" : ", oni nodir yn wahanol",
      "crown.copyright" : "© Hawlfraint y Goron",
       "ers.file.upload.wrong.file" : "Nid yw hon yn ffeil y rhoesoch wybod fod angen i chi ei huwchlwytho – dewiswch ffeil wahanol",
       "ers.file.upload.csv.file.large" : "Mae’r ffeil hon yn fwy na 100MB – dewiswch ffeil wahanol, neu e-bostiwch <a href=’mailto:gwasanaeth.cymraeg@hmrc.gsi.gov.uk’>gwasanaeth.cymraeg@hmrc.gsi.gov.uk</a> a byddwn yn eich helpu i gyflwyno’ch datganiad",
       "ers.file.upload.csv.wrong.type" : "Nid ffeil .csv yw hon – dewiswch ffeil wahanol",
       "ers.file.upload.ods.large" : "Mae’r ffeil hon yn fwy na 10MB – dewiswch ffeil wahanol, neu e-bostiwch <a href=’mailto:gwasanaeth.cymraeg@hmrc.gsi.gov.uk’>gwasanaeth.cymraeg@hmrc.gsi.gov.uk</a> a byddwn yn eich helpu i gyflwyno’ch datganiad",
       "ers.file.upload.ods.wrong.type" : "Nid ffeil .ods yw hon – dewiswch ffeil wahanol",
       "ers.file.upload.ods.too.long" : "Rhaid i enw’r ffeil gynnwys 240 o gymeriadau neu lai",
        "ers.file.upload.ods.invalid.characters" : "Mae enw’r ffeil yn cynnwys cymeriadau annilys"
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

  // Switch out OGL footer and Crown Copyright if welsh language
  if (GOVUK.playLanguage === "cy") {
    $(".footer-meta-inner .open-government-licence > p").remove();
    $(".footer-meta-inner .open-government-licence").append('<p>' +
      GOVUK.getLocalisedContent("ogl.pt.1") +
      ' <a href="http://www.nationalarchives.gov.uk/doc/open-government-licence/version/3" target="_blank">' +
      GOVUK.getLocalisedContent("ogl.link.text") +
      '</a>' +
      GOVUK.getLocalisedContent("ogl.pt.2") +
      '</p>');

    $(".footer-meta .copyright > a").text(GOVUK.getLocalisedContent("crown.copyright"));
  }
});