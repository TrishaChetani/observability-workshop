package com.github.olly.workshop.imagerotator.adapter;

import com.github.olly.workshop.imagerotator.config.LoggingContextUtil;
import com.github.olly.workshop.imagerotator.service.ImageService;
import org.slf4j.MDC;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import io.honeycomb.beeline.tracing.Beeline;

import java.io.IOException;

@RestController
@RequestMapping(value = "/api/image")
public class ImageController {

    private static final Logger LOGGER = LoggerFactory.getLogger(ImageController.class);

    @Autowired
    private ImageService imageService;

    @Autowired
    private LoggingContextUtil lcu;

    @Autowired
    private Beeline beeline;

    @PostMapping("rotate")
    public ResponseEntity rotateImage(@RequestParam("image") MultipartFile file, @RequestParam(value = "degrees") String degrees) throws IOException {

        lcu.mdcPut(file.getContentType(), degrees);
        this.beeline.getActiveSpan().addField("transformation.rotate_degrees", degrees);
        this.beeline.getActiveSpan().addField("action", "rotate");
        this.beeline.getActiveSpan().addField("content.type", file.getContentType());

        if (file.getContentType() != null &&
                !file.getContentType().startsWith("image/")) {
            this.beeline.getActiveSpan().addField("action.success", false);
            this.beeline.getActiveSpan().addField("action.failure_reason", "wrong_content_type");
            LOGGER.warn("Wrong content type uploaded: {}", file.getContentType());
            MDC.put("responseCode", String.valueOf(HttpStatus.BAD_REQUEST));
            return new ResponseEntity<>("Wrong content type uploaded: " + file.getContentType(), HttpStatus.BAD_REQUEST);
        }

        // ISSUE: we fail on floating point values
        int intDegrees = Integer.valueOf(degrees);
        LOGGER.info("Receiving {} image to rotate by {} degrees", file.getContentType(), intDegrees);

        byte[] rotatedImage = imageService.rotate(file, intDegrees);

        if (rotatedImage == null) {
            this.beeline.getActiveSpan().addField("action.success", false);
            this.beeline.getActiveSpan().addField("action.failure_reason", "internal_server_error");
            MDC.put("responseCode", String.valueOf(HttpStatus.INTERNAL_SERVER_ERROR));
            return new ResponseEntity<>("Failed to rotate image", HttpStatus.INTERNAL_SERVER_ERROR);
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.valueOf(file.getContentType()));

        LOGGER.info("Successfully rotated image");
        this.beeline.getActiveSpan().addField("action.success", true);
        MDC.put("responseCode", String.valueOf(HttpStatus.OK));
        return new ResponseEntity<>(rotatedImage, headers, HttpStatus.OK);
    }
}
