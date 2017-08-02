String.prototype.supplant = function (o) {
    return this.replace(/{([^{}]*)}/g,
        function (a, b) {
            var r = o[b];
            return typeof r === 'string' || typeof r === 'number' ? r : a;
        }
    );
};


function normalizeId(handler) {
    return handler.replace("/", "_");
}
function initList(handlers, activeHandler, dir) {
    for (var i = 0; i < handlers.length; i++) {
        var handler = handlers[i];
        var handlerId = normalizeId(handler);
        var checked = handler === activeHandler ? "checked" : "";
        $('ul.handler-list.' + dir).append(
            '<li><input id="{handlerId}" type="radio" class="handler" name="{dir}" {checked} value="{handler}"/><label for="{handlerId}">{handler}</label></li>'
                .supplant({handlerId : handlerId, handler: handler, dir: dir, checked: checked}));
    }
}

function updateGlobalToggles(config, dir) {
    if (config[dir] == "") {
        //disabled
        $('div.global-state.' + dir + ' > div.toggle').removeClass("fa-toggle-on enabled").addClass("fa-toggle-off disabled");
        $('div.global-state.' + dir + ' > div.toggle > span.status').text("off");
    } else {
        //enabled
        $('div.global-state.' + dir + ' > div.toggle').removeClass("fa-toggle-off disabled").addClass("fa-toggle-on enabled");
        $('div.global-state.' + dir + ' > div.toggle > span.status').text("on");
    }
}

function updateList(dir, activeHandler) {

    var handlerId = normalizeId(activeHandler);
    if(handlerId === "") {
        handlerId = "noop_" + dir;
    }
    var handlerList = $('ul.handler-list.' + dir);
    handlerList.find('input').prop('checked',false);
    handlerList.find('input#' + handlerId).prop('checked',true);
}
function updateConfig(config) {
    updateGlobalToggles(config, "incoming");
    updateGlobalToggles(config, "outgoing");

    updateList("incoming", config.incoming);
    updateList("outgoing", config.outgoing);
}
function selectHandler(dir, handler) {
    var data = {};
    data[dir] = handler;
    $.ajax({
        url: '/config',
        type: 'PUT',
        data: JSON.stringify(data),
        success: function (response) {
            console.log("Config updated " + JSON.stringify(response));
        }
    });
}

function setTTR(dir, form) {
    var ttr = form.elements.namedItem("ttr").value;
    var data = {
        dir: dir,
        ttr: ttr
    };
    $.ajax({
        url: '/config/ttr',
        type: 'post',
        data: JSON.stringify(data),
        success: function (response) {
            console.log("Timer created " + response)
        }
    });

    return false;
}

function setTimer(direction, toDate){

    var countDownDate = toDate.getTime();

    // Update the count down every 1 second
    var x = setInterval(function() {

        // Get todays date and time
        var now = new Date().getTime();

        // Find the distance between now an the count down date
        var distance = countDownDate - now;

        // Time calculations for days, hours, minutes and seconds
        var days = Math.floor(distance / (1000 * 60 * 60 * 24));
        var hours = Math.floor((distance % (1000 * 60 * 60 * 24)) / (1000 * 60 * 60));
        var minutes = Math.floor((distance % (1000 * 60 * 60)) / (1000 * 60));
        var seconds = Math.floor((distance % (1000 * 60)) / 1000);

        $('p.reset-timer.'+direction).text( (days == 0 ? "" : days + "d ") + (hours == 0? "" : hours + "h ") + minutes + "m " + seconds + "s ").removeClass("hidden");

        // If the count down is finished, write some text
        if (distance < 0) {
            clearInterval(x);
            $('p.reset-timer.'+direction).addClass("hidden");
        }
    }, 1000);
}

function updateStats(stats){
    for (var key in stats) {
        if (stats.hasOwnProperty(key)) {
            $('#' + key).text(stats[key]);
        }
    }
}

$(document).ready(function () {
    $.get("/config", function (config) {

        updateGlobalToggles(config, "incoming");
        updateGlobalToggles(config, "outgoing");

        initList(config.incomingHandlers, config.incoming, "incoming");
        initList(config.outgoingHandlers, config.outgoing, "outgoing");

        $('input[class="handler"]').attr("onclick", 'selectHandler(this.name, this.value)');
    });

});
eb = new EventBus(window.location.protocol + '//' + window.location.hostname + ':' + window.location.port + '/eb');



eb.onopen = function () {
    eb.registerHandler("/monitor", function (err, msg) {
        $('ul#monitor').prepend('<li>{msg}</li>'.supplant({msg: msg.body}))
    });
    eb.registerHandler("/config/update", function (err, msg) {
        var cfg = msg.body;
        updateConfig(cfg);
    });
    eb.registerHandler("/config/update/ttr", function (err, msg) {
        var cfg = msg.body;
        var now = new Date();
        now.setTime(now.getTime() + (cfg.ttr * 60 * 1000));
        setTimer(cfg.dir, now);
    });
    eb.registerHandler("/stats", function(err, msg){
        updateStats(msg.body);
    });

};
