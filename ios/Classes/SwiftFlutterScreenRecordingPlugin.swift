import Flutter
import UIKit
import ReplayKit
import Photos

public class SwiftFlutterScreenRecordingPlugin: NSObject, FlutterPlugin {
    
    let recorder = RPScreenRecorder.shared()
    
    var videoOutputURL : URL?
    var videoWriter : AVAssetWriter?
    
    var audioInput:AVAssetWriterInput!
    var videoWriterInput : AVAssetWriterInput?
    var nameVideo: String = ""
    var recordAudio: Bool = false;
    var myResult: FlutterResult?
    var warningDelay: Int = 300;
    
    public static func register(with registrar: FlutterPluginRegistrar) {
        let channel = FlutterMethodChannel(name: "flutter_screen_recording", binaryMessenger: registrar.messenger())
        let instance = SwiftFlutterScreenRecordingPlugin()
        registrar.addMethodCallDelegate(instance, channel: channel)
    }
    
    public func handle(_ call: FlutterMethodCall, result: @escaping FlutterResult) {
        
        if(call.method == "startRecordScreen"){
            myResult = result
            let args = call.arguments as? Dictionary<String, Any>
            
            self.recordAudio = (args?["audio"] as? Bool?)! ?? false
            self.nameVideo = (args?["name"] as? String)!+".mp4";
            var width = args?["width"]; // in pixels
            if(width == nil || width is NSNull) {
                width = Int32(UIScreen.main.nativeBounds.width); // pixels
            } else {
                width = Int32(width as! Int32);
            }
            var height = args?["height"] // in pixels
            if(height == nil || height is NSNull) {
                height = Int32(UIScreen.main.nativeBounds.height); // pixels
            } else {
                height = Int32(height as! Int32);
            }
            let delay = args?["delay"];
            if(delay == nil || delay is NSNull) {
                self.warningDelay = 300; // Default value
            } else {
                self.warningDelay = Int(delay as! Int)
            }
            startRecording(
                width: width as! Int32 ,
                height: height as! Int32);
            
        }else if(call.method == "stopRecordScreen"){
            if(videoWriter != nil){
                stopRecording()
                let documentsPath = NSSearchPathForDirectoriesInDomains(.documentDirectory, .userDomainMask, true)[0] as NSString
                result(String(documentsPath.appendingPathComponent(nameVideo)))
            }
            result("")
        }
    }
    
    
    
    @objc func startRecording(width: Int32, height: Int32) {
        NSLog("startRecording: w x h = \(width) x \(height) pixels");
        //Use ReplayKit to record the screen
        //Create the file path to write to
        let documentsPath = NSSearchPathForDirectoriesInDomains(.documentDirectory, .userDomainMask, true)[0] as NSString
        self.videoOutputURL = URL(fileURLWithPath: documentsPath.appendingPathComponent(nameVideo))
        
        //Check the file does not already exist by deleting it if it does
        do {
            try FileManager.default.removeItem(at: videoOutputURL!)
        } catch {
            
        }
        
        do {
            try videoWriter = AVAssetWriter(outputURL: videoOutputURL!, fileType: AVFileType.mp4)
        } catch let writerError as NSError {
            print("Error opening video file", writerError);
            videoWriter = nil;
            return;
        }
        
        //Create the video and audio settings
        if #available(iOS 11.0, *) {
            recorder.isMicrophoneEnabled = recordAudio
            
            let videoSettings: [String : Any] = [
                AVVideoCodecKey  : AVVideoCodecH264,
                AVVideoWidthKey  : NSNumber.init(value: width),
                AVVideoHeightKey : NSNumber.init(value:height),
                AVVideoCompressionPropertiesKey: [
                    //AVVideoQualityKey: 1,
                    AVVideoProfileLevelKey: AVVideoProfileLevelH264HighAutoLevel,
                    AVVideoAverageBitRateKey: 6000000
                ],
            ]
            //Create the asset writer input object which is actually used to write out the video
            self.videoWriterInput = AVAssetWriterInput(mediaType: AVMediaType.video, outputSettings: videoSettings);
            self.videoWriterInput?.expectsMediaDataInRealTime = true;
            self.videoWriter?.add(videoWriterInput!);
            
            if(recordAudio){
                let audioOutputSettings: [String : Any] = [
                    AVNumberOfChannelsKey : 2,
                    AVFormatIDKey : kAudioFormatMPEG4AAC,
                    AVSampleRateKey: 44100,
                    AVEncoderAudioQualityKey: AVAudioQuality.high.rawValue
                ]
                //Create the asset writer input object which is actually used to write out the audio
                self.audioInput = AVAssetWriterInput(mediaType: AVMediaType.audio, outputSettings: audioOutputSettings)
                self.audioInput?.expectsMediaDataInRealTime = true;
                self.videoWriter?.add(audioInput!);
            }
        }
        
        //Tell the screen recorder to start capturing and to call the handler
        if #available(iOS 11.0, *) {
            NSLog("startRecording: about to start capture after delay...");
            
            // Impose a delay to be consistent with the use of this plugin on Android
            // where a delay is necessary. It's not necessary on IOS, but we want
            // the user experience to be the same.
            DispatchQueue.main.asyncAfter(deadline: .now() + .milliseconds(self.warningDelay) ) {
                [unowned self] in
                NSLog("startRecording: ... after delay, call recorder.startCapture")
                recorder.startCapture(handler: { (cmSampleBuffer, rpSampleType, error) in
                    guard error == nil else {
                        //Handle error
                        print("Error starting capture");
                        self.myResult!(false)
                        return;
                    }
                    
                    switch rpSampleType {
                    case RPSampleBufferType.video:
                        //                         NSLog("startRecording: Writing video...");
                        if self.videoWriter?.status == AVAssetWriter.Status.unknown {
                            self.myResult!(true)
                            self.videoWriter?.startWriting()
                            self.videoWriter?.startSession(atSourceTime:  CMSampleBufferGetPresentationTimeStamp(cmSampleBuffer))
                        }else if self.videoWriter?.status == AVAssetWriter.Status.writing {
                            if (self.videoWriterInput?.isReadyForMoreMediaData == true) {
                                //                                print("Append sample...");
                                if  self.videoWriterInput?.append(cmSampleBuffer) == false {
                                    print("Problems writing video")
                                    self.myResult!(false)
                                }
                            }
                        }
                    case RPSampleBufferType.audioMic:
                        if(self.recordAudio){
                            //                            print("Writing audio....");
                            if self.audioInput?.isReadyForMoreMediaData == true {
                                //                                NSLog("startRecording: starting audio....");
                                if self.audioInput?.append(cmSampleBuffer) == false {
                                    print("Problems writing audio")
                                }
                            }
                        }
                    default:
                        //                       print("not a video sample, so ignore");
                        break;
                    }
                } ){(error) in
                    guard error == nil else {
                        //Handle error
                        print("Screen record not allowed");
                        self.myResult!(false)
                        return;
                    }
                }
            }
        } else {
            //Fallback on earlier versions
            NSLog("Screen recording is not available for iOS versions before iOS 11");
        }
        NSLog("startRecording: return")
    }
    
    @objc func stopRecording() {
        //Stop Recording the screen
        if #available(iOS 11.0, *) {
            recorder.stopCapture( handler: { (error) in
                NSLog("Stopping recording...");
            })
        } else {
            //  Fallback on earlier versions
        }
        
        self.videoWriterInput?.markAsFinished();
        if(self.recordAudio) {
            self.audioInput?.markAsFinished();
        }
        
        self.videoWriter?.finishWriting {
            NSLog("Finished writing video");
            //Now save the video
            PHPhotoLibrary.shared().performChanges({
                PHAssetChangeRequest.creationRequestForAssetFromVideo(atFileURL: self.videoOutputURL!)
            })
        }
        
    }
    
}
